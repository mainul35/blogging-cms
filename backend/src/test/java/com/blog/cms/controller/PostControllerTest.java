package com.blog.cms.controller;

import com.blog.cms.dto.PostRequest;
import com.blog.cms.dto.PostResponse;
import com.blog.cms.service.PostService;
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
class PostControllerTest {

    @Mock private PostService postService;

    private PostController controller;

    @BeforeEach
    void setUp() {
        controller = new PostController(postService);
    }

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("admin@blog.com", null, List.of());
    }

    @Test
    void getAllPosts_delegatesStatusQueryParam() {
        when(postService.getAllPosts("PUBLISHED")).thenReturn(Flux.just(PostResponse.builder().id(1L).build()));

        StepVerifier.create(controller.getAllPosts("PUBLISHED")).expectNextCount(1).verifyComplete();
    }

    @Test
    void getPostById_delegatesId() {
        when(postService.getPostById(5L)).thenReturn(Mono.just(PostResponse.builder().id(5L).build()));

        StepVerifier.create(controller.getPostById(5L))
                .assertNext(r -> org.assertj.core.api.Assertions.assertThat(r.getId()).isEqualTo(5L))
                .verifyComplete();
    }

    @Test
    void getPostBySlug_delegatesSlug() {
        when(postService.getPostBySlug("hello-world")).thenReturn(Mono.just(PostResponse.builder().slug("hello-world").build()));

        StepVerifier.create(controller.getPostBySlug("hello-world")).expectNextCount(1).verifyComplete();
    }

    @Test
    void createPost_delegatesRequestAndAuthenticatedEmail() {
        PostRequest request = new PostRequest();
        request.setTitle("Title");
        request.setContent("Content");
        when(postService.createPost(request, "admin@blog.com")).thenReturn(Mono.just(PostResponse.builder().id(1L).build()));

        StepVerifier.create(controller.createPost(request, auth())).expectNextCount(1).verifyComplete();
    }

    @Test
    void updatePost_delegatesIdAndRequest() {
        PostRequest request = new PostRequest();
        request.setTitle("Title");
        request.setContent("Content");
        when(postService.updatePost(7L, request)).thenReturn(Mono.just(PostResponse.builder().id(7L).build()));

        StepVerifier.create(controller.updatePost(7L, request)).expectNextCount(1).verifyComplete();
    }

    @Test
    void deletePost_delegatesId() {
        when(postService.deletePost(9L)).thenReturn(Mono.empty());

        StepVerifier.create(controller.deletePost(9L)).verifyComplete();

        verify(postService).deletePost(9L);
    }
}
