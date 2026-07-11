package com.blog.cms.service;

import com.blog.cms.dto.CommentRequest;
import com.blog.cms.dto.CommentResponse;
import com.blog.cms.model.Comment;
import com.blog.cms.model.CommentMention;
import com.blog.cms.model.Reader;
import com.blog.cms.repository.CommentMentionRepository;
import com.blog.cms.repository.CommentRepository;
import com.blog.cms.repository.PostRepository;
import com.blog.cms.repository.ReaderRepository;
import com.blog.cms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
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
    private final ReaderRepository readerRepository;

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

    public Mono<CommentResponse> addComment(String slug, CommentRequest request, Authentication auth) {
        return postRepository.findBySlug(slug)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found")))
                .flatMap(post -> buildComment(post.getId(), request, auth))
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

    public Mono<Void> deleteComment(Long id, Authentication auth) {
        return commentRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found")))
                .flatMap(comment -> {
                    if (hasRole(auth, "ADMIN")) {
                        return userRepository.findByEmail(auth.getName())
                                .flatMap(user -> {
                                    boolean isOwner = comment.getAuthorId() != null
                                            && comment.getAuthorId().equals(user.getId());
                                    boolean isAdmin = "ADMIN".equals(user.getRole());
                                    if (!isOwner && !isAdmin) {
                                        return Mono.<Void>error(new ResponseStatusException(
                                                HttpStatus.FORBIDDEN, "Not authorized to delete this comment"));
                                    }
                                    return commentRepository.delete(comment);
                                });
                    }
                    if (hasRole(auth, "READER")) {
                        return readerRepository.findByHandle(auth.getName())
                                .flatMap(reader -> {
                                    boolean isOwner = comment.getReaderId() != null
                                            && comment.getReaderId().equals(reader.getId());
                                    if (!isOwner) {
                                        return Mono.<Void>error(new ResponseStatusException(
                                                HttpStatus.FORBIDDEN, "Not authorized to delete this comment"));
                                    }
                                    return commentRepository.delete(comment);
                                });
                    }
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "Not authorized to delete this comment"));
                });
    }

    // --- private helpers ---

    private boolean hasRole(Authentication auth, String role) {
        if (auth == null) return false;
        String target = "ROLE_" + role;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (target.equals(authority.getAuthority())) return true;
        }
        return false;
    }

    private Mono<Comment> buildComment(Long postId, CommentRequest request, Authentication auth) {
        if (hasRole(auth, "ADMIN")) {
            return userRepository.findByEmail(auth.getName())
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
        if (hasRole(auth, "READER")) {
            return readerRepository.findByHandle(auth.getName())
                    .map(reader -> Comment.builder()
                            .postId(postId)
                            .readerId(reader.getId())
                            .authorName(reader.getDisplayName())
                            .authorEmail(reader.getEmail())
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

        // Readers are the common case now (most @mentions target other
        // commenters, not the blog owner), so try that lookup first.
        return Flux.fromIterable(handles)
                .flatMap(handle -> readerRepository.findByHandle(handle)
                        .map(reader -> CommentMention.builder()
                                .commentId(comment.getId())
                                .mentionedReaderId(reader.getId())
                                .build())
                        .switchIfEmpty(userRepository.findByUsername(handle)
                                .map(user -> CommentMention.builder()
                                        .commentId(comment.getId())
                                        .mentionedUserId(user.getId())
                                        .build()))
                        .flatMap(commentMentionRepository::save)
                        // If handle resolves to neither a reader nor the admin, skip silently
                        .onErrorResume(e -> Mono.empty())
                )
                .then();
    }

    private Mono<CommentResponse> toResponse(Comment comment) {
        Mono<List<String>> mentionsMono = commentMentionRepository.findAllByCommentId(comment.getId())
                .flatMap(mention -> mention.getMentionedReaderId() != null
                        ? readerRepository.findById(mention.getMentionedReaderId()).map(Reader::getHandle)
                        : userRepository.findById(mention.getMentionedUserId())
                                .map(u -> u.getUsername() != null ? u.getUsername() : u.getEmail()))
                .collectList();

        Mono<CommentResponse.CommentResponseBuilder> identityMono = resolveIdentity(comment);

        return Mono.zip(mentionsMono, identityMono)
                .map(tuple -> tuple.getT2()
                        .id(comment.getId())
                        .body(comment.getBody())
                        .authorName(comment.getAuthorName())
                        .parentId(comment.getParentId())
                        .mentionedUsernames(tuple.getT1())
                        .createdAt(comment.getCreatedAt())
                        .build());
    }

    // Resolves the author-identity fields (type/handle/avatar) without changing
    // the response shape used above — kept as a builder in progress so toResponse
    // can zip it alongside the mentions lookup.
    private Mono<CommentResponse.CommentResponseBuilder> resolveIdentity(Comment comment) {
        if (comment.getAuthorId() != null) {
            return userRepository.findById(comment.getAuthorId())
                    .map(user -> CommentResponse.builder()
                            .authorType("ADMIN")
                            .authorHandle(user.getUsername())
                            .authorAvatarUrl(user.getAvatarUrl()))
                    .defaultIfEmpty(CommentResponse.builder().authorType("ADMIN"));
        }
        if (comment.getReaderId() != null) {
            return readerRepository.findById(comment.getReaderId())
                    .map(reader -> CommentResponse.builder()
                            .authorType("READER")
                            .authorHandle(reader.getHandle())
                            .authorAvatarUrl(reader.getAvatarUrl()))
                    .defaultIfEmpty(CommentResponse.builder().authorType("READER"));
        }
        return Mono.just(CommentResponse.builder().authorType("GUEST"));
    }
}
