package com.blog.cms.mail;

import com.blog.cms.model.MailSettings;
import com.blog.cms.repository.MailSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

// The single MailSender bean AuthService/NewsletterService depend on. Reads
// the current provider + credentials from the DB on every send() rather than
// picking a fixed implementation at startup via @ConditionalOnProperty --
// that's what makes the provider configurable from the Settings UI / setup
// wizard without a restart. Mail volume on a personal blog is low enough that
// a DB read per send (instead of caching) isn't worth the added invalidation
// complexity.
@Component
@RequiredArgsConstructor
public class MailSenderRouter implements MailSender {

    private final MailSettingsRepository mailSettingsRepository;

    @Override
    public Mono<Void> send(MailMessage message) {
        return mailSettingsRepository.findById(1L)
                .flatMap(settings -> dispatch(message, settings))
                // No row (shouldn't happen -- seeded by V11) falls back to the
                // zero-config default rather than failing the caller's request.
                // Mono.defer is required here: switchIfEmpty's argument is
                // evaluated eagerly, and LogMailSender.send() logs as a side
                // effect the moment it's called -- without defer, every send
                // would log unconditionally before the empty-check even runs.
                .switchIfEmpty(Mono.defer(() -> LogMailSender.send(message)));
    }

    private Mono<Void> dispatch(MailMessage message, MailSettings settings) {
        return switch (settings.getProvider()) {
            case "smtp" -> SmtpMailSender.send(message, settings);
            case "resend" -> ResendMailSender.send(message, settings);
            case "sendgrid" -> SendGridMailSender.send(message, settings);
            default -> LogMailSender.send(message);
        };
    }
}
