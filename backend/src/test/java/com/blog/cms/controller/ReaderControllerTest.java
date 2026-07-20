package com.blog.cms.controller;

import com.blog.cms.dto.OAuthLoginRequest;
import com.blog.cms.dto.ReaderAuthResponse;
import com.blog.cms.service.ReaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReaderControllerTest {

    @Mock private ReaderService readerService;

    private ReaderController controller;

    @BeforeEach
    void setUp() {
        controller = new ReaderController(readerService);
    }

    @Test
    void oauthLogin_delegatesHeaderSecretAndRequest() {
        OAuthLoginRequest request = new OAuthLoginRequest();
        request.setProvider("GOOGLE");
        request.setProviderUserId("g-123");
        request.setEmail("jane@example.com");
        request.setDisplayName("Jane");
        ReaderAuthResponse response = ReaderAuthResponse.builder().token("jwt").handle("jane").build();
        when(readerService.oauthLogin("the-secret", request)).thenReturn(Mono.just(response));

        StepVerifier.create(controller.oauthLogin("the-secret", request)).expectNext(response).verifyComplete();
    }
}
