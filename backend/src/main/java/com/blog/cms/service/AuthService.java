package com.blog.cms.service;

import com.blog.cms.dto.AuthRequest;
import com.blog.cms.dto.AuthResponse;
import com.blog.cms.dto.ChangePasswordRequest;
import com.blog.cms.dto.EmergencyResetResponse;
import com.blog.cms.dto.ProfileResponse;
import com.blog.cms.dto.UpdateProfileRequest;
import com.blog.cms.mail.MailMessage;
import com.blog.cms.mail.MailSender;
import com.blog.cms.model.User;
import com.blog.cms.repository.UserRepository;
import com.blog.cms.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(1);
    private static final String GENERIC_FORGOT_PASSWORD_MESSAGE =
            "If that email is registered, a password reset link has been sent.";
    private static final String MAIL_NOT_CONFIGURED_MESSAGE =
            "Password reset via email isn't set up for this site yet. Contact the site administrator.";
    private static final int FORGOT_PASSWORD_MAX_REQUESTS = 5;
    private static final Duration FORGOT_PASSWORD_RATE_WINDOW = Duration.ofMinutes(15);
    private static final int EMERGENCY_RESET_MAX_REQUESTS = 5;
    private static final Duration EMERGENCY_RESET_RATE_WINDOW = Duration.ofHours(1);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final MailSender mailSender;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final MailSettingsService mailSettingsService;

    @Value("${app.admin.reset-secret}")
    private String resetSecret;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public Mono<AuthResponse> login(AuthRequest request) {
        return userRepository.findByEmail(request.getEmail())
                .filter(user -> passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")))
                .map(this::toAuthResponse);
    }

    public Mono<ProfileResponse> getProfile(String email) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")))
                .map(user -> ProfileResponse.builder()
                        .email(user.getEmail())
                        .name(user.getUsername())
                        .avatarUrl(user.getAvatarUrl())
                        .role(user.getRole())
                        .build());
    }

    public Mono<AuthResponse> updateProfile(String currentEmail, UpdateProfileRequest request) {
        return userRepository.findByEmail(currentEmail)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")))
                .flatMap(user -> {
                    boolean emailChanged = !user.getEmail().equalsIgnoreCase(request.getEmail());
                    Mono<Boolean> emailTaken = emailChanged
                            ? userRepository.existsByEmail(request.getEmail())
                            : Mono.just(false);
                    return emailTaken.flatMap(taken -> {
                        if (taken) {
                            return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use"));
                        }
                        user.setEmail(request.getEmail());
                        user.setUsername(request.getName());
                        user.setAvatarUrl(request.getAvatarUrl());
                        return userRepository.save(user);
                    });
                })
                .map(this::toAuthResponse);
    }

    private AuthResponse toAuthResponse(User user) {
        return AuthResponse.builder()
                .token(jwtUtil.generateToken(user.getEmail(), user.getRole()))
                .email(user.getEmail())
                .role(user.getRole())
                .name(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }

    public Mono<Void> changePassword(String email, ChangePasswordRequest request) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")))
                .filter(user -> passwordEncoder.matches(request.getCurrentPassword(), user.getPassword()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect")))
                .flatMap(user -> {
                    user.setPassword(passwordEncoder.encode(request.getNewPassword()));
                    return userRepository.save(user);
                })
                .then();
    }

    // Always resolves to the same generic message whether or not the email
    // matched an account, and whether or not the send itself succeeded — this
    // is the standard anti-enumeration shape for a "forgot password" endpoint,
    // distinct from the secret-gated resetAdminToDefault() below (that one is
    // an emergency nuke-to-defaults escape hatch; this one is the normal
    // self-service path a real user would use day to day).
    public Mono<String> forgotPassword(String email) {
        // Told plainly rather than pretending to work -- if mail isn't
        // configured, a "check your inbox" message would be actively
        // misleading (nothing will ever arrive). This one message is static
        // and identical for every caller regardless of email, so it doesn't
        // weaken the anti-enumeration property of the message below it.
        return mailSettingsService.isMailConfigured()
                .flatMap(configured -> configured ? doForgotPassword(email) : Mono.just(MAIL_NOT_CONFIGURED_MESSAGE));
    }

    private Mono<String> doForgotPassword(String email) {
        // Checked before the DB lookup, and never surfaced to the caller either
        // way (still the same generic message) — an attacker spamming this
        // endpoint shouldn't be able to tell "rate limited" apart from "email
        // doesn't exist" apart from "email exists, link sent".
        return isWithinRateLimit(email)
                .flatMap(allowed -> allowed
                        ? userRepository.findByEmail(email)
                                .flatMap(user -> {
                                    String rawToken = UUID.randomUUID().toString();
                                    // Store only the hash — if the database were ever read
                                    // by someone who shouldn't (a leak, a misconfigured
                                    // backup, an over-privileged query), a stored hash can't
                                    // be replayed as a reset link the way the raw token
                                    // could. Same idea as never storing plaintext passwords.
                                    user.setResetToken(hashToken(rawToken));
                                    user.setResetTokenExpiresAt(LocalDateTime.now().plus(RESET_TOKEN_TTL));
                                    return userRepository.save(user)
                                            .flatMap(saved -> sendResetEmail(saved.getEmail(), rawToken));
                                })
                        : Mono.empty())
                .thenReturn(GENERIC_FORGOT_PASSWORD_MESSAGE);
    }

    private Mono<Boolean> isWithinRateLimit(String email) {
        String key = "forgot-password-rate:" + email.toLowerCase();
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> count == 1
                        ? redisTemplate.expire(key, FORGOT_PASSWORD_RATE_WINDOW).thenReturn(true)
                        : Mono.just(count <= FORGOT_PASSWORD_MAX_REQUESTS))
                // If Redis is unreachable, fail open rather than blocking a
                // legitimate password reset over an infra hiccup.
                .onErrorResume(e -> {
                    log.warn("Rate-limit check failed for forgot-password, allowing request: {}", e.getMessage());
                    return Mono.just(true);
                });
    }

    public Mono<Void> resetPassword(String token, String newPassword) {
        return userRepository.findByResetToken(hashToken(token))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid or expired reset link")))
                .flatMap(user -> {
                    if (user.getResetTokenExpiresAt() == null
                            || user.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Invalid or expired reset link"));
                    }
                    user.setPassword(passwordEncoder.encode(newPassword));
                    user.setResetToken(null);
                    user.setResetTokenExpiresAt(null);
                    return userRepository.save(user);
                })
                .then();
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; this can't actually happen.
            throw new IllegalStateException(e);
        }
    }

    private Mono<Void> sendResetEmail(String email, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        return mailSender.send(MailMessage.builder()
                        .to(email)
                        .subject("Reset your password")
                        .text("Click to reset your password: " + link + "\nThis link expires in 1 hour.")
                        .build())
                .onErrorResume(e -> {
                    log.warn("Failed to send password reset email to {}: {}", email, e.getMessage());
                    return Mono.empty();
                });
    }

    // Keyed by role, not by email, since the admin can change their email via
    // updateProfile — this must still find the account if they did. Email is
    // deliberately left untouched (no reason to reset it just to recover a
    // password, and doing so used to silently overwrite a real custom email
    // with a hardcoded placeholder).
    //
    // Generates a fresh random password on every call rather than resetting to
    // a fixed default — a hardcoded default (previously admin@blog.com /
    // Admin@1234, both plainly readable in this repo's own application.yml)
    // meant that anyone who ever obtained ADMIN_RESET_SECRET — e.g. via
    // another leak like the one already documented in the Troubleshooting &
    // Incident Log — would know the resulting login outright, with nothing
    // left to guess. A random password means the secret alone isn't enough;
    // the caller only learns the new password because this response returns
    // it directly to them, once, over the same authenticated call.
    public Mono<EmergencyResetResponse> resetAdminToDefault(String providedSecret) {
        if (providedSecret == null || !resetSecret.equals(providedSecret)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid reset key"));
        }
        return isWithinEmergencyResetRateLimit()
                .flatMap(allowed -> {
                    if (!allowed) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.TOO_MANY_REQUESTS, "Too many reset attempts — try again later"));
                    }
                    String newPassword = generateRandomPassword();
                    return userRepository.findFirstByRole("ADMIN")
                            .switchIfEmpty(Mono.error(new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "Admin account not found")))
                            .flatMap(user -> {
                                user.setPassword(passwordEncoder.encode(newPassword));
                                return userRepository.save(user);
                            })
                            .doOnNext(user -> log.warn("Emergency admin reset invoked — password rotated for {}", user.getEmail()))
                            .map(user -> new EmergencyResetResponse(user.getEmail(), newPassword));
                });
    }

    private Mono<Boolean> isWithinEmergencyResetRateLimit() {
        String key = "emergency-reset-rate";
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> count == 1
                        ? redisTemplate.expire(key, EMERGENCY_RESET_RATE_WINDOW).thenReturn(true)
                        : Mono.just(count <= EMERGENCY_RESET_MAX_REQUESTS))
                // Fail open on Redis errors, same reasoning as the forgot-password
                // limiter — an infra hiccup shouldn't lock out a legitimate,
                // already-secret-holding recovery attempt.
                .onErrorResume(e -> {
                    log.warn("Rate-limit check failed for emergency-reset, allowing request: {}", e.getMessage());
                    return Mono.just(true);
                });
    }

    private String generateRandomPassword() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
