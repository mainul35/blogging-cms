package com.blog.cms.mail;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

// Zero-config default -- the original stub behavior, preserved so existing
// installs see no change until someone opts into a real provider. Not a
// Spring bean -- see SmtpMailSender for why.
@Slf4j
public class LogMailSender {

    public static Mono<Void> send(MailMessage message) {
        log.info("[EMAIL] To: {} | Subject: {} | Body: {}", message.getTo(), message.getSubject(), message.getText());
        return Mono.empty();
    }
}
