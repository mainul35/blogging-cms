package com.blog.cms.controller;

import com.blog.cms.dto.AuthRequest;
import com.blog.cms.dto.AuthResponse;
import com.blog.cms.dto.EmergencyResetResponse;
import com.blog.cms.dto.ForgotPasswordRequest;
import com.blog.cms.dto.ResetPasswordRequest;
import com.blog.cms.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Controllers here are thin delegation only -- these tests verify the right
// service method is called with the right arguments and the result passes
// through untouched, not business logic (already covered by each service's
// own test class).
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
    }

    @Test
    void login_delegatesToAuthService() {
        AuthRequest request = new AuthRequest();
        request.setEmail("admin@blog.com");
        request.setPassword("password123");
        AuthResponse response = AuthResponse.builder().token("jwt").email("admin@blog.com").build();
        when(authService.login(request)).thenReturn(Mono.just(response));

        StepVerifier.create(controller.login(request)).expectNext(response).verifyComplete();
    }

    @Test
    void forgotPassword_delegatesEmailOnly() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("admin@blog.com");
        when(authService.forgotPassword("admin@blog.com")).thenReturn(Mono.just("generic message"));

        StepVerifier.create(controller.forgotPassword(request)).expectNext("generic message").verifyComplete();
    }

    @Test
    void resetPassword_delegatesTokenAndNewPassword() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("tok");
        request.setNewPassword("new-pass-123");
        when(authService.resetPassword("tok", "new-pass-123")).thenReturn(Mono.empty());

        StepVerifier.create(controller.resetPassword(request)).verifyComplete();

        verify(authService).resetPassword("tok", "new-pass-123");
    }

    @Test
    void emergencyReset_passesHeaderSecretThrough() {
        EmergencyResetResponse response = new EmergencyResetResponse("admin@blog.com", "random-pass");
        when(authService.resetAdminToDefault("the-secret")).thenReturn(Mono.just(response));

        StepVerifier.create(controller.emergencyReset("the-secret")).expectNext(response).verifyComplete();
    }

    @Test
    void emergencyReset_missingHeader_passesNullThrough() {
        when(authService.resetAdminToDefault(null)).thenReturn(
                Mono.error(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN)));

        StepVerifier.create(controller.emergencyReset(null))
                .expectError(org.springframework.web.server.ResponseStatusException.class)
                .verify();
    }
}
