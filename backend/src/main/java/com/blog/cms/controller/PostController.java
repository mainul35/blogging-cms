package com.blog.cms.controller;

import com.blog.cms.dto.PostRequest;
import com.blog.cms.dto.PostResponse;
import com.blog.cms.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public Flux<PostResponse> getAllPosts(@RequestParam(defaultValue = "PUBLISHED") String status) {
        return postService.getAllPosts(status);
    }

    @GetMapping("/id/{id}")
    public Mono<PostResponse> getPostById(@PathVariable Long id) {
        return postService.getPostById(id);
    }

    @GetMapping("/{slug}")
    public Mono<PostResponse> getPostBySlug(@PathVariable String slug) {
        return postService.getPostBySlug(slug);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PostResponse> createPost(@RequestBody @Valid PostRequest request, Authentication auth) {
        return postService.createPost(request, auth.getName());
    }

    @PutMapping("/{id}")
    public Mono<PostResponse> updatePost(@PathVariable Long id, @RequestBody @Valid PostRequest request) {
        return postService.updatePost(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deletePost(@PathVariable Long id) {
        return postService.deletePost(id);
    }
}
