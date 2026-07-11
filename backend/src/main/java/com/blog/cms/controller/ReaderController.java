package com.blog.cms.controller;

import com.blog.cms.dto.OAuthLoginRequest;
import com.blog.cms.dto.ReaderAuthResponse;
import com.blog.cms.service.ReaderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/readers")
@RequiredArgsConstructor
public class ReaderController {

    private final ReaderService readerService;

    // Called server-side by the frontend's NextAuth callback only, after it has
    // already verified the OAuth identity with Google/GitHub — gated by a shared
    // secret rather than Spring Security, same pattern as /api/auth/emergency-reset.
    @PostMapping("/oauth-login")
    public Mono<ReaderAuthResponse> oauthLogin(
            @RequestHeader(value = "X-Internal-Auth-Secret", required = false) String secret,
            @RequestBody @Valid OAuthLoginRequest request) {
        return readerService.oauthLogin(secret, request);
    }
}
