package com.blog.cms.controller;

import com.blog.cms.dto.AuthRequest;
import com.blog.cms.dto.AuthResponse;
import com.blog.cms.dto.EmergencyResetResponse;
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

    // Not linked from the frontend. Recovers the admin account when the
    // password has been forgotten — gated by a shared secret
    // (app.admin.reset-secret / ADMIN_RESET_SECRET env var), not by login.
    // Rotates the password to a freshly generated random value and returns it
    // here, once — see AuthService.resetAdminToDefault for why a fixed
    // default password is deliberately not used.
    @PostMapping("/emergency-reset")
    public Mono<EmergencyResetResponse> emergencyReset(@RequestHeader(value = "X-Admin-Reset-Secret", required = false) String secret) {
        return authService.resetAdminToDefault(secret);
    }
}
