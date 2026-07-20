package com.blog.cms.controller;

import com.blog.cms.dto.AuthResponse;
import com.blog.cms.dto.ChangePasswordRequest;
import com.blog.cms.dto.CommentResponse;
import com.blog.cms.dto.ProfileResponse;
import com.blog.cms.dto.UpdateProfileRequest;
import com.blog.cms.repository.PostRepository;
import com.blog.cms.service.AuthService;
import com.blog.cms.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private PostRepository postRepository;
    @Mock private CommentService commentService;
    @Mock private AuthService authService;

    private AdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminController(postRepository, commentService, authService);
    }

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("admin@blog.com", null, List.of());
    }

    @Test
    void getStats_zipsThreeCounts() {
        when(postRepository.count()).thenReturn(Mono.just(10L));
        when(postRepository.countByStatus("PUBLISHED")).thenReturn(Mono.just(7L));
        when(postRepository.countByStatus("DRAFT")).thenReturn(Mono.just(3L));

        StepVerifier.create(controller.getStats())
                .assertNext(stats -> {
                    assertThat(stats.get("totalPosts")).isEqualTo(10L);
                    assertThat(stats.get("publishedPosts")).isEqualTo(7L);
                    assertThat(stats.get("draftPosts")).isEqualTo(3L);
                })
                .verifyComplete();
    }

    @Test
    void getAllComments_delegatesToCommentService() {
        when(commentService.getAllComments()).thenReturn(Flux.just(CommentResponse.builder().id(1L).build()));

        StepVerifier.create(controller.getAllComments()).expectNextCount(1).verifyComplete();
    }

    @Test
    void changePassword_delegatesWithAuthenticatedEmail() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("old");
        request.setNewPassword("newpass12");
        when(authService.changePassword("admin@blog.com", request)).thenReturn(Mono.empty());

        StepVerifier.create(controller.changePassword(request, auth())).verifyComplete();

        verify(authService).changePassword("admin@blog.com", request);
    }

    @Test
    void getProfile_delegatesWithAuthenticatedEmail() {
        when(authService.getProfile("admin@blog.com")).thenReturn(
                Mono.just(ProfileResponse.builder().email("admin@blog.com").build()));

        StepVerifier.create(controller.getProfile(auth()))
                .assertNext(p -> assertThat(p.getEmail()).isEqualTo("admin@blog.com"))
                .verifyComplete();
    }

    @Test
    void updateProfile_delegatesWithAuthenticatedEmail() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("new@blog.com");
        when(authService.updateProfile("admin@blog.com", request)).thenReturn(
                Mono.just(AuthResponse.builder().email("new@blog.com").build()));

        StepVerifier.create(controller.updateProfile(request, auth()))
                .assertNext(r -> assertThat(r.getEmail()).isEqualTo("new@blog.com"))
                .verifyComplete();
    }
}
