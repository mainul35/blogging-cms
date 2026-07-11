package com.blog.cms.controller;

import com.blog.cms.dto.AuthRequest;
import com.blog.cms.dto.AuthResponse;
import com.blog.cms.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Mono<AuthResponse> login(@RequestBody @Valid AuthRequest request) {
        return authService.login(request);
    }

    // Not linked from the frontend. Recovers the admin account to its default
    // credentials when the password has been forgotten — gated by a shared
    // secret (app.admin.reset-secret / ADMIN_RESET_SECRET env var), not by login.
    @PostMapping("/emergency-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> emergencyReset(@RequestHeader(value = "X-Admin-Reset-Secret", required = false) String secret) {
        return authService.resetAdminToDefault(secret);
    }
}
