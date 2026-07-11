package com.blog.cms.controller;

import com.blog.cms.dto.NewsletterSubscribeRequest;
import com.blog.cms.model.NewsletterSubscriber;
import com.blog.cms.service.NewsletterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
public class NewsletterController {

    private final NewsletterService newsletterService;

    // --- Public endpoints ---

    @PostMapping("/api/newsletter/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<String> subscribe(@RequestBody @Valid NewsletterSubscribeRequest request) {
        return newsletterService.subscribe(request.getEmail());
    }

    @GetMapping("/api/newsletter/confirm")
    public Mono<String> confirm(@RequestParam String token) {
        return newsletterService.confirm(token);
    }

    // --- Admin endpoints (protected by /api/admin/** → hasRole("ADMIN") in SecurityConfig) ---

    @GetMapping("/api/admin/newsletter/subscribers")
    public Flux<NewsletterSubscriber> getSubscribers() {
        return newsletterService.getSubscribers();
    }

    @PostMapping("/api/admin/newsletter/send")
    public Mono<String> sendDigest(@RequestParam Long postId) {
        return newsletterService.sendDigest(postId);
    }
}
