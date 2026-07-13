package com.blog.cms.controller;

import com.blog.cms.dto.AuthRequest;
import com.blog.cms.dto.AuthResponse;
import com.blog.cms.dto.ForgotPasswordRequest;
import com.blog.cms.dto.ResetPasswordRequest;
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

    // Public, self-service — sends a time-limited reset link if the email is
    // registered. Always returns the same message either way (see AuthService).
    @PostMapping("/forgot-password")
    public Mono<String> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        return authService.forgotPassword(request.getEmail());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        return authService.resetPassword(request.getToken(), request.getNewPassword());
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
