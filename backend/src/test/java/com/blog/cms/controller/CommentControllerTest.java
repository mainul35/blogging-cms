package com.blog.cms.controller;

import com.blog.cms.dto.CommentRequest;
import com.blog.cms.dto.CommentResponse;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    @Mock private CommentService commentService;

    private CommentController controller;

    @BeforeEach
    void setUp() {
        controller = new CommentController(commentService);
    }

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("admin@blog.com", null, List.of());
    }

    @Test
    void getComments_delegatesSlug() {
        when(commentService.getComments("hello-world")).thenReturn(Flux.just(CommentResponse.builder().id(1L).build()));

        StepVerifier.create(controller.getComments("hello-world")).expectNextCount(1).verifyComplete();
    }

    @Test
    void addComment_delegatesSlugRequestAndFullAuthentication() {
        CommentRequest request = new CommentRequest();
        request.setBody("Nice post");
        Authentication auth = auth();
        when(commentService.addComment("hello-world", request, auth))
                .thenReturn(Mono.just(CommentResponse.builder().id(2L).build()));

        StepVerifier.create(controller.addComment("hello-world", request, auth)).expectNextCount(1).verifyComplete();
    }

    @Test
    void deleteComment_delegatesIdAndAuthentication() {
        Authentication auth = auth();
        when(commentService.deleteComment(3L, auth)).thenReturn(Mono.empty());

        StepVerifier.create(controller.deleteComment(3L, auth)).verifyComplete();

        verify(commentService).deleteComment(3L, auth);
    }
}
