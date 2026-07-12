package com.blog.cms.mail;

import reactor.core.publisher.Mono;

public interface MailSender {
    Mono<Void> send(MailMessage message);
}
