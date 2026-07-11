package com.blog.cms.controller;

import com.blog.cms.dto.AuthResponse;
import com.blog.cms.dto.ChangePasswordRequest;
import com.blog.cms.dto.CommentResponse;
import com.blog.cms.dto.ProfileResponse;
import com.blog.cms.dto.UpdateProfileRequest;
import com.blog.cms.repository.PostRepository;
import com.blog.cms.service.AuthService;
import com.blog.cms.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final PostRepository postRepository;
    private final CommentService commentService;
    private final AuthService authService;

    @GetMapping("/stats")
    public Mono<Map<String, Long>> getStats() {
        return Mono.zip(
                postRepository.count(),
                postRepository.countByStatus("PUBLISHED"),
                postRepository.countByStatus("DRAFT")
        ).map(t -> Map.of(
                "totalPosts", t.getT1(),
                "publishedPosts", t.getT2(),
                "draftPosts", t.getT3()
        ));
    }

    @GetMapping("/comments")
    public Flux<CommentResponse> getAllComments() {
        return commentService.getAllComments();
    }

    @PutMapping("/account/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request, Authentication auth) {
        return authService.changePassword(auth.getName(), request);
    }

    @GetMapping("/account/profile")
    public Mono<ProfileResponse> getProfile(Authentication auth) {
        return authService.getProfile(auth.getName());
    }

    @PutMapping("/account/profile")
    public Mono<AuthResponse> updateProfile(@RequestBody @Valid UpdateProfileRequest request, Authentication auth) {
        return authService.updateProfile(auth.getName(), request);
    }
}
