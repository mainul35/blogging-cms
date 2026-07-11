package com.blog.cms.service;

import com.blog.cms.dto.PostRequest;
import com.blog.cms.dto.PostResponse;
import com.blog.cms.model.Post;
import com.blog.cms.repository.CategoryRepository;
import com.blog.cms.repository.PostRepository;
import com.blog.cms.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CacheService cacheService;
    private final UploadService uploadService;

    public Flux<PostResponse> getAllPosts(String status) {
        Flux<Post> posts = "all".equalsIgnoreCase(status)
                ? postRepository.findAll()
                : postRepository.findAllByStatus(status.toUpperCase());
        return posts.flatMap(this::toResponse);
    }

    public Mono<PostResponse> getPostById(Long id) {
        return postRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found")))
                .flatMap(this::toResponse);
    }

    public Mono<PostResponse> getPostBySlug(String slug) {
        return cacheService.getPost(slug)
                .switchIfEmpty(
                        postRepository.findBySlug(slug)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found")))
                                .flatMap(this::toResponse)
                                .flatMap(response -> cacheService.cachePost(slug, response).thenReturn(response))
                );
    }

    public Mono<PostResponse> createPost(PostRequest request, String authorEmail) {
        return userRepository.findByEmail(authorEmail)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Author not found")))
                .flatMap(author -> {
                    boolean publishing = "PUBLISHED".equalsIgnoreCase(request.getStatus());
                    Post post = Post.builder()
                            .title(request.getTitle())
                            .slug(slugify(request.getTitle()))
                            .content(request.getContent())
                            .excerpt(request.getExcerpt())
                            .coverImageUrl(request.getCoverImageUrl())
                            .status(publishing ? "PUBLISHED" : "DRAFT")
                            .authorId(author.getId())
                            .categoryId(request.getCategoryId())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .publishedAt(publishing ? LocalDateTime.now() : null)
                            .build();
                    return postRepository.save(post);
                })
                .flatMap(this::toResponse);
    }

    public Mono<PostResponse> updatePost(Long id, PostRequest request) {
        return postRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found")))
                .flatMap(post -> {
                    Set<String> before = uploadService.extractLocalFilenames(post.getContent(), post.getCoverImageUrl());

                    boolean publishing = "PUBLISHED".equalsIgnoreCase(request.getStatus())
                            && !"PUBLISHED".equals(post.getStatus());
                    post.setTitle(request.getTitle());
                    post.setContent(request.getContent());
                    post.setExcerpt(request.getExcerpt());
                    post.setCoverImageUrl(request.getCoverImageUrl());
                    post.setStatus(request.getStatus() != null ? request.getStatus().toUpperCase() : post.getStatus());
                    post.setCategoryId(request.getCategoryId());
                    post.setUpdatedAt(LocalDateTime.now());
                    if (publishing) post.setPublishedAt(LocalDateTime.now());

                    Set<String> after = uploadService.extractLocalFilenames(post.getContent(), post.getCoverImageUrl());
                    Set<String> removed = new HashSet<>(before);
                    removed.removeAll(after);

                    return postRepository.save(post)
                            .flatMap(saved -> cleanupOrphanedImages(saved.getId(), removed).thenReturn(saved));
                })
                .flatMap(post -> cacheService.evictPost(post.getSlug()).thenReturn(post))
                .flatMap(this::toResponse);
    }

    public Mono<Void> deletePost(Long id) {
        return postRepository.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found")))
                .flatMap(post -> {
                    Set<String> images = uploadService.extractLocalFilenames(post.getContent(), post.getCoverImageUrl());
                    return postRepository.delete(post)
                            .then(cacheService.evictPost(post.getSlug()))
                            .then(cleanupOrphanedImages(id, images));
                });
    }

    // Only deletes a file if no other post still references it.
    private Mono<Void> cleanupOrphanedImages(Long postId, Set<String> filenames) {
        if (filenames.isEmpty()) return Mono.empty();
        return Flux.fromIterable(filenames)
                .filterWhen(filename -> postRepository.countOtherPostsReferencingFile(postId, filename)
                        .map(count -> count == 0))
                .collect(Collectors.toSet())
                .flatMap(uploadService::deleteFiles);
    }

    private Mono<PostResponse> toResponse(Post post) {
        Mono<String> authorName = userRepository.findById(post.getAuthorId())
                .map(u -> u.getUsername() != null ? u.getUsername() : u.getEmail())
                .defaultIfEmpty("Unknown");

        Mono<String> categoryName = post.getCategoryId() != null
                ? categoryRepository.findById(post.getCategoryId()).map(c -> c.getName()).defaultIfEmpty("")
                : Mono.just("");

        return Mono.zip(authorName, categoryName)
                .map(tuple -> PostResponse.builder()
                        .id(post.getId())
                        .title(post.getTitle())
                        .slug(post.getSlug())
                        .excerpt(post.getExcerpt())
                        .content(post.getContent())
                        .coverImageUrl(post.getCoverImageUrl())
                        .status(post.getStatus())
                        .authorName(tuple.getT1())
                        .categoryName(tuple.getT2())
                        .tags(List.of())
                        .createdAt(post.getCreatedAt())
                        .updatedAt(post.getUpdatedAt())
                        .publishedAt(post.getPublishedAt())
                        .build());
    }

    private String slugify(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }
}
