package com.blog.cms.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

// Zero-config default — the original stub behavior, preserved so existing
// installs see no change until someone opts into a real provider below.
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "log", matchIfMissing = true)
@Slf4j
public class LogMailSender implements MailSender {

    @Override
    public Mono<Void> send(MailMessage message) {
        log.info("[EMAIL] To: {} | Subject: {} | Body: {}", message.getTo(), message.getSubject(), message.getText());
        return Mono.empty();
    }
}
