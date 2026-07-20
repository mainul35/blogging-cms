package com.blog.cms.controller;

import com.blog.cms.dto.NewsletterSubscribeRequest;
import com.blog.cms.model.NewsletterSubscriber;
import com.blog.cms.service.NewsletterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterControllerTest {

    @Mock private NewsletterService newsletterService;

    private NewsletterController controller;

    @BeforeEach
    void setUp() {
        controller = new NewsletterController(newsletterService);
    }

    @Test
    void subscribe_delegatesEmailFromBody() {
        NewsletterSubscribeRequest request = new NewsletterSubscribeRequest();
        request.setEmail("reader@example.com");
        when(newsletterService.subscribe("reader@example.com")).thenReturn(Mono.just("Check your inbox"));

        StepVerifier.create(controller.subscribe(request)).expectNext("Check your inbox").verifyComplete();
    }

    @Test
    void confirm_delegatesTokenQueryParam() {
        when(newsletterService.confirm("tok")).thenReturn(Mono.just("confirmed"));

        StepVerifier.create(controller.confirm("tok")).expectNext("confirmed").verifyComplete();
    }

    @Test
    void getSubscribers_delegates() {
        when(newsletterService.getSubscribers()).thenReturn(
                Flux.just(NewsletterSubscriber.builder().id(1L).build()));

        StepVerifier.create(controller.getSubscribers()).expectNextCount(1).verifyComplete();
    }

    @Test
    void sendDigest_delegatesPostIdQueryParam() {
        when(newsletterService.sendDigest(42L)).thenReturn(Mono.just("Digest queued for 3 subscriber(s)"));

        StepVerifier.create(controller.sendDigest(42L)).expectNext("Digest queued for 3 subscriber(s)").verifyComplete();
    }
}
