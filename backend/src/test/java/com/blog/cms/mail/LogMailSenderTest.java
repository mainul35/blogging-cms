package com.blog.cms.mail;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class LogMailSenderTest {

    @Test
    void send_alwaysCompletesEmptyWithoutError() {
        MailMessage message = MailMessage.builder().to("reader@example.com").subject("Hi").text("Body").build();

        StepVerifier.create(LogMailSender.send(message)).verifyComplete();
    }
}
