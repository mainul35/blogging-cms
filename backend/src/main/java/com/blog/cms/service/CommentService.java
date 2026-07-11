package com.blog.cms.service;

import com.blog.cms.dto.CommentRequest;
import com.blog.cms.dto.CommentResponse;
import com.blog.cms.model.Comment;
import com.blog.cms.model.CommentMention;
import com.blog.cms.repository.CommentMentionRepository;
import com.blog.cms.repository.CommentRepository;
import com.blog.cms.repository.PostRepository;
import com.blog.cms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentMentionRepository commentMentionRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_]+)");

    public Flux<CommentResponse> getComments(String slug) {
        return postRepository.findBySlug(slug)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found")))
                .flatMapMany(post -> commentRepository.findAllByPostIdOrderByCreatedAtAsc(post.getId())
                        .flatMap(this::toResponse)
                        .collectList()
                        .flatMapMany(comments -> {
                            // Build a threaded tree in memory — all comments for one post fit easily
                            Map<Long, CommentResponse> index = new LinkedHashMap<>();
                            for (CommentResponse c : comments) index.put(c.getId(), c);

                            List<CommentResponse> roots = new ArrayList<>();
                            for (CommentResponse c : comments) {
                                if (c.getParentId() == null) {
                                    roots.add(c);
                                } else {
                                    CommentResponse parent = index.get(c.getParentId());
                                    if (parent != null) parent.getReplies().add(c);
                                }
                            }
                            return Flux.fromIterable(roots);
                        })
                );
    }

    public Mono<CommentResponse> addComment(String slug, CommentRequest request, String authorEmail) {
        return postRepository.findBySlug(slug)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found")))
                .flatMap(post -> buildComment(post.getId(), request, authorEmail))
                .flatMap(commentRepository::save)
                .flatMap(saved -> saveMentions(saved).thenReturn(saved))
                .flatMap(this::toResponse);
    }

    public Flux<CommentResponse> getAllComments() {
        return commentRepository.findAll()
                .flatMap(comment ->
                        Mono.zip(
                                toResponse(comment),
                                postRepository.findById(comment.getPostId())
                                        .map(p -> new String[]{p.getTitle(), p.getSlug()})
                                        .defaultIfEmpty(new String[]{"Unknown Post", ""})
                        ).map(tuple -> {
                            CommentResponse r = tuple.getT1();
                            r.setPostTitle(tuple.getT2()[0]);
                            r.setPostSlug(tuple.getT2()[1]);
                            return r;
                        })
                )
                // Newest first for the moderation view
                .sort((a, b) -> {
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                });
    }

    public Mono<Void> deleteComment(Long id, String requesterEmail) {
        return commentRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found")))
                .flatMap(comment -> userRepository.findByEmail(requesterEmail)
                        .flatMap(user -> {
                            boolean isOwner = comment.getAuthorId() != null
                                    && comment.getAuthorId().equals(user.getId());
                            boolean isAdmin = "ADMIN".equals(user.getRole());
                            if (!isOwner && !isAdmin) {
                                return Mono.<Void>error(new ResponseStatusException(
                                        HttpStatus.FORBIDDEN, "Not authorized to delete this comment"));
                            }
                            return commentRepository.delete(comment);
                        })
                );
    }

    // --- private helpers ---

    private Mono<Comment> buildComment(Long postId, CommentRequest request, String authorEmail) {
        if (authorEmail != null) {
            return userRepository.findByEmail(authorEmail)
                    .map(user -> Comment.builder()
                            .postId(postId)
                            .authorId(user.getId())
                            .authorName(user.getUsername() != null ? user.getUsername() : user.getEmail())
                            .authorEmail(user.getEmail())
                            .body(request.getBody())
                            .parentId(request.getParentId())
                            .createdAt(LocalDateTime.now())
                            .build());
        }
        // Guest comment — name and email are mandatory
        if (request.getAuthorName() == null || request.getAuthorEmail() == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Name and email are required for guest comments"));
        }
        return Mono.just(Comment.builder()
                .postId(postId)
                .authorName(request.getAuthorName())
                .authorEmail(request.getAuthorEmail())
                .body(request.getBody())
                .parentId(request.getParentId())
                .createdAt(LocalDateTime.now())
                .build());
    }

    private Mono<Void> saveMentions(Comment comment) {
        Matcher matcher = MENTION_PATTERN.matcher(comment.getBody());
        Set<String> handles = new LinkedHashSet<>();
        while (matcher.find()) handles.add(matcher.group(1));

        if (handles.isEmpty()) return Mono.empty();

        return Flux.fromIterable(handles)
                .flatMap(handle -> userRepository.findByUsername(handle)
                        .flatMap(user -> {
                            log.info("@mention: @{} (userId={}) in comment {}", handle, user.getId(), comment.getId());
                            return commentMentionRepository.save(
                                    CommentMention.builder()
                                            .commentId(comment.getId())
                                            .mentionedUserId(user.getId())
                                            .build()
                            );
                        })
                        // If username not found, skip silently
                        .onErrorResume(e -> Mono.empty())
                )
                .then();
    }

    private Mono<CommentResponse> toResponse(Comment comment) {
        return commentMentionRepository.findAllByCommentId(comment.getId())
                .flatMap(mention -> userRepository.findById(mention.getMentionedUserId())
                        .map(u -> u.getUsername() != null ? u.getUsername() : u.getEmail()))
                .collectList()
                .map(mentions -> CommentResponse.builder()
                        .id(comment.getId())
                        .body(comment.getBody())
                        .authorName(comment.getAuthorName())
                        .parentId(comment.getParentId())
                        .mentionedUsernames(mentions)
                        .createdAt(comment.getCreatedAt())
                        .build());
    }
}
