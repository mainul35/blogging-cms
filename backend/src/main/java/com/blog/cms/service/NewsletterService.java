package com.blog.cms.service;

import com.blog.cms.mail.MailMessage;
import com.blog.cms.mail.MailSender;
import com.blog.cms.model.NewsletterSubscriber;
import com.blog.cms.repository.NewsletterRepository;
import com.blog.cms.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterService {

    private final NewsletterRepository newsletterRepository;
    private final PostRepository postRepository;
    private final MailSender mailSender;

    @Value("${app.public-url}")
    private String publicUrl;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public Mono<String> subscribe(String email) {
        return newsletterRepository.existsByEmail(email)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT, "This email is already subscribed"));
                    }
                    String token = UUID.randomUUID().toString();
                    return newsletterRepository.save(
                            NewsletterSubscriber.builder()
                                    .email(email)
                                    .confirmed(false)
                                    .token(token)
                                    .subscribedAt(LocalDateTime.now())
                                    .build()
                    ).flatMap(saved -> sendConfirmationEmail(email, token))
                     .thenReturn("Check your inbox to confirm your subscription.");
                });
    }

    public Mono<String> confirm(String token) {
        return newsletterRepository.findByToken(token)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Invalid or expired confirmation token")))
                .flatMap(subscriber -> {
                    if (subscriber.isConfirmed()) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.CONFLICT, "This email is already confirmed"));
                    }
                    subscriber.setConfirmed(true);
                    return newsletterRepository.save(subscriber);
                })
                .thenReturn("Subscription confirmed — you will now receive updates.");
    }

    public Flux<NewsletterSubscriber> getSubscribers() {
        return newsletterRepository.findAllByConfirmed(true);
    }

    public Mono<String> sendDigest(Long postId) {
        return postRepository.findById(postId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Post not found")))
                .flatMap(post -> {
                    String link = frontendUrl + "/post/" + post.getSlug();
                    return newsletterRepository.findAllByConfirmed(true)
                            .flatMap(sub -> sendDigestEmail(sub.getEmail(), post.getTitle(), link).thenReturn(sub))
                            .count()
                            .map(count -> "Digest queued for " + count + " subscriber(s) — post: \"" + post.getTitle() + "\"");
                });
    }

    private Mono<Void> sendConfirmationEmail(String email, String token) {
        String link = publicUrl + "/api/newsletter/confirm?token=" + token;
        return mailSender.send(MailMessage.builder()
                        .to(email)
                        .subject("Confirm your subscription")
                        .text("Click to confirm your subscription: " + link)
                        .build())
                .onErrorResume(e -> {
                    log.warn("Failed to send confirmation email to {}: {}", email, e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> sendDigestEmail(String email, String title, String link) {
        return mailSender.send(MailMessage.builder()
                        .to(email)
                        .subject("New post: " + title)
                        .text("\"" + title + "\" was just published.\nRead it here: " + link)
                        .build())
                .onErrorResume(e -> {
                    log.warn("Failed to send digest email to {}: {}", email, e.getMessage());
                    return Mono.empty();
                });
    }
}
