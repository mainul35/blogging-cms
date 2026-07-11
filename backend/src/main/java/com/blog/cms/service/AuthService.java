package com.blog.cms.service;

import com.blog.cms.dto.AuthRequest;
import com.blog.cms.dto.AuthResponse;
import com.blog.cms.dto.ChangePasswordRequest;
import com.blog.cms.dto.ProfileResponse;
import com.blog.cms.dto.UpdateProfileRequest;
import com.blog.cms.model.User;
import com.blog.cms.repository.UserRepository;
import com.blog.cms.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${app.admin.default-email}")
    private String defaultAdminEmail;

    @Value("${app.admin.default-password}")
    private String defaultAdminPassword;

    @Value("${app.admin.reset-secret}")
    private String resetSecret;

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

    // Keyed by role, not by the default email, since the admin can change their
    // email via updateProfile — this must still find the account if they did.
    public Mono<Void> resetAdminToDefault(String providedSecret) {
        if (providedSecret == null || !resetSecret.equals(providedSecret)) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid reset key"));
        }
        return userRepository.findFirstByRole("ADMIN")
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin account not found")))
                .flatMap(user -> {
                    user.setEmail(defaultAdminEmail);
                    user.setPassword(passwordEncoder.encode(defaultAdminPassword));
                    return userRepository.save(user);
                })
                .then();
    }
}
