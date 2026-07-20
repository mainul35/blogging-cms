package com.blog.cms.service;

import com.blog.cms.mail.MailMessage;
import com.blog.cms.mail.MailSender;
import com.blog.cms.model.NewsletterSubscriber;
import com.blog.cms.model.Post;
import com.blog.cms.repository.NewsletterRepository;
import com.blog.cms.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterServiceTest {

    @Mock private NewsletterRepository newsletterRepository;
    @Mock private PostRepository postRepository;
    @Mock private MailSender mailSender;
    @Mock private MailSettingsService mailSettingsService;

    private NewsletterService newsletterService;

    @BeforeEach
    void setUp() {
        newsletterService = new NewsletterService(newsletterRepository, postRepository, mailSender, mailSettingsService);
        ReflectionTestUtils.setField(newsletterService, "publicUrl", "https://blog.example.com");
        ReflectionTestUtils.setField(newsletterService, "frontendUrl", "https://blog.example.com");
    }

    // ---- subscribe ----

    @Test
    void subscribe_mailNotConfigured_isServiceUnavailable_neverTouchesRepository() {
        when(mailSettingsService.isMailConfigured()).thenReturn(Mono.just(false));

        StepVerifier.create(newsletterService.subscribe("reader@example.com"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(503))
                .verify();

        verify(newsletterRepository, never()).existsByEmail(any());
    }

    @Test
    void subscribe_newEmail_savesUnconfirmedAndSendsConfirmationEmail() {
        when(mailSettingsService.isMailConfigured()).thenReturn(Mono.just(true));
        when(newsletterRepository.existsByEmail("reader@example.com")).thenReturn(Mono.just(false));
        when(newsletterRepository.save(any(NewsletterSubscriber.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(mailSender.send(any(MailMessage.class))).thenReturn(Mono.empty());

        StepVerifier.create(newsletterService.subscribe("reader@example.com"))
                .assertNext(msg -> assertThat(msg).contains("Check your inbox"))
                .verifyComplete();

        verify(newsletterRepository).save(argThat(s -> !s.isConfirmed() && s.getToken() != null));
        verify(mailSender).send(any(MailMessage.class));
    }

    @Test
    void subscribe_alreadySubscribedEmail_isConflict_sendsNoEmail() {
        when(mailSettingsService.isMailConfigured()).thenReturn(Mono.just(true));
        when(newsletterRepository.existsByEmail("reader@example.com")).thenReturn(Mono.just(true));

        StepVerifier.create(newsletterService.subscribe("reader@example.com"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(409))
                .verify();

        verify(mailSender, never()).send(any());
    }

    @Test
    void subscribe_mailSendFails_stillReturnsSuccessMessage() {
        // sendConfirmationEmail swallows send errors (onErrorResume) -- the
        // subscriber row is already saved, so the endpoint shouldn't fail
        // just because the notification email itself had a transient issue.
        when(mailSettingsService.isMailConfigured()).thenReturn(Mono.just(true));
        when(newsletterRepository.existsByEmail("reader@example.com")).thenReturn(Mono.just(false));
        when(newsletterRepository.save(any(NewsletterSubscriber.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(mailSender.send(any(MailMessage.class))).thenReturn(Mono.error(new RuntimeException("smtp down")));

        StepVerifier.create(newsletterService.subscribe("reader@example.com"))
                .assertNext(msg -> assertThat(msg).contains("Check your inbox"))
                .verifyComplete();
    }

    // ---- confirm ----

    @Test
    void confirm_validUnconfirmedToken_marksConfirmed() {
        NewsletterSubscriber sub = NewsletterSubscriber.builder()
                .id(1L).email("reader@example.com").confirmed(false).token("tok").build();
        when(newsletterRepository.findByToken("tok")).thenReturn(Mono.just(sub));
        when(newsletterRepository.save(any(NewsletterSubscriber.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(newsletterService.confirm("tok"))
                .assertNext(msg -> assertThat(msg).contains("confirmed"))
                .verifyComplete();

        verify(newsletterRepository).save(argThat(NewsletterSubscriber::isConfirmed));
    }

    @Test
    void confirm_unknownToken_isNotFound() {
        when(newsletterRepository.findByToken("bogus")).thenReturn(Mono.empty());

        StepVerifier.create(newsletterService.confirm("bogus"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    @Test
    void confirm_alreadyConfirmedToken_isConflict() {
        NewsletterSubscriber sub = NewsletterSubscriber.builder()
                .id(1L).email("reader@example.com").confirmed(true).token("tok").build();
        when(newsletterRepository.findByToken("tok")).thenReturn(Mono.just(sub));

        StepVerifier.create(newsletterService.confirm("tok"))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(409))
                .verify();

        verify(newsletterRepository, never()).save(any());
    }

    // ---- getSubscribers ----

    @Test
    void getSubscribers_returnsOnlyConfirmed() {
        when(newsletterRepository.findAllByConfirmed(true)).thenReturn(Flux.just(
                NewsletterSubscriber.builder().id(1L).email("a@x.com").confirmed(true).build()));

        StepVerifier.create(newsletterService.getSubscribers())
                .expectNextCount(1)
                .verifyComplete();

        verify(newsletterRepository).findAllByConfirmed(true);
    }

    // ---- sendDigest ----

    @Test
    void sendDigest_unknownPost_isNotFound() {
        when(postRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(newsletterService.sendDigest(999L))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(404))
                .verify();
    }

    @Test
    void sendDigest_sendsToEveryConfirmedSubscriber_reportsCount() {
        Post post = Post.builder().id(1L).slug("my-post").title("My Post").build();
        when(postRepository.findById(1L)).thenReturn(Mono.just(post));
        when(newsletterRepository.findAllByConfirmed(true)).thenReturn(Flux.just(
                NewsletterSubscriber.builder().id(1L).email("a@x.com").confirmed(true).build(),
                NewsletterSubscriber.builder().id(2L).email("b@x.com").confirmed(true).build()));
        when(mailSender.send(any(MailMessage.class))).thenReturn(Mono.empty());

        StepVerifier.create(newsletterService.sendDigest(1L))
                .assertNext(msg -> {
                    assertThat(msg).contains("2 subscriber");
                    assertThat(msg).contains("My Post");
                })
                .verifyComplete();

        verify(mailSender, times(2)).send(any(MailMessage.class));
    }

    @Test
    void sendDigest_oneSubscriberSendFails_othersStillReceiveDigest() {
        Post post = Post.builder().id(1L).slug("my-post").title("My Post").build();
        when(postRepository.findById(1L)).thenReturn(Mono.just(post));
        when(newsletterRepository.findAllByConfirmed(true)).thenReturn(Flux.just(
                NewsletterSubscriber.builder().id(1L).email("bad@x.com").confirmed(true).build(),
                NewsletterSubscriber.builder().id(2L).email("good@x.com").confirmed(true).build()));
        // Mockito re-evaluates already-registered argThat matchers against the
        // raw (null) placeholder invocation used internally while setting up
        // each subsequent when(...) stub -- these predicates must tolerate a
        // null argument themselves, or that setup step NPEs before either
        // stub is ever exercised by real code.
        when(mailSender.send(argThat((MailMessage m) -> m != null && "bad@x.com".equals(m.getTo()))))
                .thenReturn(Mono.error(new RuntimeException("bounced")));
        when(mailSender.send(argThat((MailMessage m) -> m != null && "good@x.com".equals(m.getTo()))))
                .thenReturn(Mono.empty());

        StepVerifier.create(newsletterService.sendDigest(1L))
                .assertNext(msg -> assertThat(msg).contains("2 subscriber"))
                .verifyComplete();

        verify(mailSender).send(argThat((MailMessage m) -> m != null && "good@x.com".equals(m.getTo())));
    }

    private static <T> T argThat(java.util.function.Predicate<T> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
