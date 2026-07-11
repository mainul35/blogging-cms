package com.blog.cms.controller;

import com.blog.cms.dto.CommentRequest;
import com.blog.cms.dto.CommentResponse;
import com.blog.cms.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // Public — returns threaded comment tree for a post
    @GetMapping("/api/posts/{slug}/comments")
    public Flux<CommentResponse> getComments(@PathVariable String slug) {
        return commentService.getComments(slug);
    }

    // Open to admins, signed-in readers, and guests (guest requires authorName +
    // authorEmail in body). The full Authentication (not just its name) is passed
    // through, since admin vs reader subjects must be disambiguated by role.
    @PostMapping("/api/posts/{slug}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<CommentResponse> addComment(
            @PathVariable String slug,
            @RequestBody @Valid CommentRequest request,
            Authentication auth) {
        return commentService.addComment(slug, request, auth);
    }

    // Requires authentication; service enforces owner-or-admin rule
    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteComment(@PathVariable Long id, Authentication auth) {
        return commentService.deleteComment(id, auth);
    }
}
