package com.blog.cms.service;

import com.blog.cms.dto.AuthRequest;
import com.blog.cms.dto.AuthResponse;
import com.blog.cms.dto.ChangePasswordRequest;
import com.blog.cms.dto.EmergencyResetResponse;
import com.blog.cms.dto.ForgotPasswordRequest;
import com.blog.cms.dto.ProfileResponse;
import com.blog.cms.dto.UpdateProfileRequest;
import com.blog.cms.mail.MailMessage;
import com.blog.cms.mail.MailSender;
import com.blog.cms.model.User;
import com.blog.cms.repository.UserRepository;
import com.blog.cms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Plain Mockito, no Spring context -- AuthService's dependencies are all
// interfaces/beans that are cheap to mock directly, and spinning up a real
// ApplicationContext (or even @DataR2dbcTest) would be much slower for no
// extra coverage here. @Value fields (resetSecret, frontendUrl) are set via
// ReflectionTestUtils since there's no Spring context to resolve them.
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private MailSender mailSender;
    @Mock private ReactiveRedisTemplate<String, Object> redisTemplate;
    @Mock private ReactiveValueOperations<String, Object> valueOperations;
    @Mock private MailSettingsService mailSettingsService;

    private AuthService authService;

    private static final String RESET_SECRET = "test-reset-secret";

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtUtil, mailSender,
                redisTemplate, mailSettingsService);
        ReflectionTestUtils.setField(authService, "resetSecret", RESET_SECRET);
        ReflectionTestUtils.setField(authService, "frontendUrl", "https://blog.example.com");
        // Not every test touches Redis (e.g. login/profile), so stub leniently
        // rather than per-test -- avoids Mockito's "unnecessary stubbing" strictness
        // failing tests that never call opsForValue().
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private User adminUser() {
        return User.builder()
                .id(1L)
                .email("admin@blog.com")
                .username("Admin")
                .password("hashed-password")
                .role("ADMIN")
                .avatarUrl(null)
                .build();
    }

    // ---- login ----

    @Test
    void login_correctCredentials_returnsAuthResponseWithToken() {
        User user = adminUser();
        AuthRequest request = new AuthRequest();
        request.setEmail("admin@blog.com");
        request.setPassword("plaintext");

        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("plaintext", "hashed-password")).thenReturn(true);
        when(jwtUtil.generateToken("admin@blog.com", "ADMIN")).thenReturn("signed-jwt");

        StepVerifier.create(authService.login(request))
                .assertNext(response -> {
                    assertThat(response.getToken()).isEqualTo("signed-jwt");
                    assertThat(response.getEmail()).isEqualTo("admin@blog.com");
                    assertThat(response.getRole()).isEqualTo("ADMIN");
                })
                .verifyComplete();
    }

    @Test
    void login_wrongPassword_isUnauthorized() {
        User user = adminUser();
        AuthRequest request = new AuthRequest();
        request.setEmail("admin@blog.com");
        request.setPassword("wrong");

        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        StepVerifier.create(authService.login(request))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(401))
                .verify();
    }

    @Test
    void login_unknownEmail_isUnauthorized() {
        AuthRequest request = new AuthRequest();
        request.setEmail("nobody@blog.com");
        request.setPassword("whatever");

        when(userRepository.findByEmail("nobody@blog.com")).thenReturn(Mono.empty());

        StepVerifier.create(authService.login(request))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(401))
                .verify();
    }

    // ---- getProfile ----

    @Test
    void getProfile_existingUser_mapsFields() {
        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(adminUser()));

        StepVerifier.create(authService.getProfile("admin@blog.com"))
                .assertNext((ProfileResponse p) -> {
                    assertThat(p.getEmail()).isEqualTo("admin@blog.com");
                    assertThat(p.getName()).isEqualTo("Admin");
                    assertThat(p.getRole()).isEqualTo("ADMIN");
                })
                .verifyComplete();
    }

    @Test
    void getProfile_missingUser_isNotFound() {
        when(userRepository.findByEmail("ghost@blog.com")).thenReturn(Mono.empty());

        StepVerifier.create(authService.getProfile("ghost@blog.com"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    // ---- updateProfile ----

    @Test
    void updateProfile_emailUnchanged_savesWithoutUniquenessCheck() {
        User user = adminUser();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("admin@blog.com");
        request.setName("New Name");
        request.setAvatarUrl("/uploads/avatar.png");

        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(jwtUtil.generateToken(eq("admin@blog.com"), eq("ADMIN"))).thenReturn("new-jwt");

        StepVerifier.create(authService.updateProfile("admin@blog.com", request))
                .assertNext((AuthResponse r) -> {
                    assertThat(r.getName()).isEqualTo("New Name");
                    assertThat(r.getToken()).isEqualTo("new-jwt");
                })
                .verifyComplete();

        verify(userRepository, never()).existsByEmail(anyString());
    }

    @Test
    void updateProfile_emailChangedToAvailableAddress_succeeds() {
        User user = adminUser();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("new@blog.com");
        request.setName("Admin");

        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(user));
        when(userRepository.existsByEmail("new@blog.com")).thenReturn(Mono.just(false));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(jwtUtil.generateToken(eq("new@blog.com"), eq("ADMIN"))).thenReturn("jwt");

        StepVerifier.create(authService.updateProfile("admin@blog.com", request))
                .assertNext(r -> assertThat(r.getEmail()).isEqualTo("new@blog.com"))
                .verifyComplete();
    }

    @Test
    void updateProfile_emailChangedButAlreadyTaken_isConflict() {
        User user = adminUser();
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("taken@blog.com");
        request.setName("Admin");

        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(user));
        when(userRepository.existsByEmail("taken@blog.com")).thenReturn(Mono.just(true));

        StepVerifier.create(authService.updateProfile("admin@blog.com", request))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(409))
                .verify();

        verify(userRepository, never()).save(any());
    }

    // ---- changePassword ----

    @Test
    void changePassword_correctCurrentPassword_encodesAndSaves() {
        User user = adminUser();
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old-plain");
        request.setNewPassword("new-plain-pass");

        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("old-plain", "hashed-password")).thenReturn(true);
        when(passwordEncoder.encode("new-plain-pass")).thenReturn("new-hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(authService.changePassword("admin@blog.com", request))
                .verifyComplete();

        verify(userRepository).save(argThatPasswordIs("new-hashed"));
    }

    @Test
    void changePassword_wrongCurrentPassword_isUnauthorized() {
        User user = adminUser();
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrong");
        request.setNewPassword("new-plain-pass");

        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(user));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        StepVerifier.create(authService.changePassword("admin@blog.com", request))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(401))
                .verify();

        verify(userRepository, never()).save(any());
    }

    private User argThatPasswordIs(String expectedHash) {
        return org.mockito.ArgumentMatchers.argThat(u -> expectedHash.equals(u.getPassword()));
    }

    // ---- forgotPassword ----

    @Test
    void forgotPassword_mailNotConfigured_returnsStaticMessage_neverTouchesRedisOrRepo() {
        when(mailSettingsService.isMailConfigured()).thenReturn(Mono.just(false));

        StepVerifier.create(authService.forgotPassword("admin@blog.com"))
                .assertNext(msg -> assertThat(msg).contains("isn't set up"))
                .verifyComplete();

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void forgotPassword_mailConfigured_knownEmail_sendsResetEmail_returnsGenericMessage() {
        User user = adminUser();
        when(mailSettingsService.isMailConfigured()).thenReturn(Mono.just(true));
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(mailSender.send(any(MailMessage.class))).thenReturn(Mono.empty());

        StepVerifier.create(authService.forgotPassword("admin@blog.com"))
                .assertNext(msg -> assertThat(msg).contains("password reset link has been sent"))
                .verifyComplete();

        verify(mailSender).send(any(MailMessage.class));
        // The stored token must be hashed, never the raw UUID -- can't predict
        // the raw value here, but this at least confirms a save happened with
        // resetToken populated and non-blank.
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(
                u -> u.getResetToken() != null && !u.getResetToken().isBlank()));
    }

    @Test
    void forgotPassword_unknownEmail_stillReturnsGenericMessage_sendsNothing() {
        when(mailSettingsService.isMailConfigured()).thenReturn(Mono.just(true));
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        when(userRepository.findByEmail("ghost@blog.com")).thenReturn(Mono.empty());

        StepVerifier.create(authService.forgotPassword("ghost@blog.com"))
                .assertNext(msg -> assertThat(msg).contains("password reset link has been sent"))
                .verifyComplete();

        verify(mailSender, never()).send(any());
    }

    @Test
    void forgotPassword_rateLimitExceeded_skipsLookup_stillGenericMessage() {
        when(mailSettingsService.isMailConfigured()).thenReturn(Mono.just(true));
        // 6th request within the window
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(6L));

        StepVerifier.create(authService.forgotPassword("admin@blog.com"))
                .assertNext(msg -> assertThat(msg).contains("password reset link has been sent"))
                .verifyComplete();

        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test
    void forgotPassword_redisUnavailable_failsOpenAndStillProceeds() {
        when(mailSettingsService.isMailConfigured()).thenReturn(Mono.just(true));
        when(valueOperations.increment(anyString())).thenReturn(Mono.error(new RuntimeException("redis down")));
        when(userRepository.findByEmail("admin@blog.com")).thenReturn(Mono.just(adminUser()));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(mailSender.send(any(MailMessage.class))).thenReturn(Mono.empty());

        StepVerifier.create(authService.forgotPassword("admin@blog.com"))
                .assertNext(msg -> assertThat(msg).contains("password reset link has been sent"))
                .verifyComplete();

        verify(mailSender).send(any());
    }

    // ---- resetPassword ----

    @Test
    void resetPassword_validUnexpiredToken_updatesPasswordAndClearsToken() {
        User user = adminUser();
        user.setResetToken("irrelevant-because-hashToken-is-private");
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(30));

        when(userRepository.findByResetToken(anyString())).thenReturn(Mono.just(user));
        when(passwordEncoder.encode("brand-new-pass")).thenReturn("new-hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(authService.resetPassword("raw-token", "brand-new-pass"))
                .verifyComplete();

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(
                u -> "new-hashed".equals(u.getPassword()) && u.getResetToken() == null
                        && u.getResetTokenExpiresAt() == null));
    }

    @Test
    void resetPassword_unknownToken_isBadRequest() {
        when(userRepository.findByResetToken(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(authService.resetPassword("bogus-token", "whatever12"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(400))
                .verify();
    }

    @Test
    void resetPassword_expiredToken_isBadRequest_doesNotSave() {
        User user = adminUser();
        user.setResetToken("some-hash");
        user.setResetTokenExpiresAt(LocalDateTime.now().minusMinutes(5));

        when(userRepository.findByResetToken(anyString())).thenReturn(Mono.just(user));

        StepVerifier.create(authService.resetPassword("raw-token", "whatever12"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(400))
                .verify();

        verify(userRepository, never()).save(any());
    }

    // ---- resetAdminToDefault (emergency reset) ----

    @Test
    void emergencyReset_wrongSecret_isForbidden_neverChecksRateLimit() {
        StepVerifier.create(authService.resetAdminToDefault("not-the-secret"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403))
                .verify();

        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void emergencyReset_nullSecret_isForbidden() {
        StepVerifier.create(authService.resetAdminToDefault(null))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403))
                .verify();
    }

    @Test
    void emergencyReset_correctSecret_withinRateLimit_rotatesToRandomPasswordAndKeepsEmail() {
        User admin = adminUser();
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        when(userRepository.findFirstByRole("ADMIN")).thenReturn(Mono.just(admin));
        when(passwordEncoder.encode(anyString())).thenReturn("new-random-hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(authService.resetAdminToDefault(RESET_SECRET))
                .assertNext((EmergencyResetResponse r) -> {
                    assertThat(r.getEmail()).isEqualTo("admin@blog.com");
                    // A fresh random password every call -- not the old Flyway
                    // default -- see AuthService's own comment on why.
                    assertThat(r.getNewPassword()).isNotBlank();
                })
                .verifyComplete();

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(
                u -> "new-random-hashed".equals(u.getPassword()) && "admin@blog.com".equals(u.getEmail())));
    }

    @Test
    void emergencyReset_rateLimitExceeded_isTooManyRequests_neverTouchesRepository() {
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(6L));

        StepVerifier.create(authService.resetAdminToDefault(RESET_SECRET))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(429))
                .verify();

        verify(userRepository, never()).findFirstByRole(anyString());
    }

    @Test
    void emergencyReset_noAdminAccountExists_isNotFound() {
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any())).thenReturn(Mono.just(true));
        when(userRepository.findFirstByRole("ADMIN")).thenReturn(Mono.empty());

        StepVerifier.create(authService.resetAdminToDefault(RESET_SECRET))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }
}
