package com.blog.cms.mail;

import com.blog.cms.model.MailSettings;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// Not a Spring bean -- see SmtpMailSender for why (constructed per send() call
// from the current DB-stored MailSettings by MailSenderRouter).
public class ResendMailSender {

    private static final String RESEND_BASE_URL = "https://api.resend.com";

    public static Mono<Void> send(MailMessage message, MailSettings settings) {
        return send(message, settings, RESEND_BASE_URL);
    }

    // Package-private overload purely for testability -- lets a same-package
    // test point this at a local JDK HttpServer instead of the real Resend
    // API. The public 2-arg overload above always uses the real endpoint;
    // this doesn't change behavior for any real caller.
    static Mono<Void> send(MailMessage message, MailSettings settings, String baseUrl) {
        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + settings.getResendApiKey())
                .build();

        Map<String, Object> body = new HashMap<>();
        body.put("from", settings.getFromAddress());
        body.put("to", message.getTo());
        body.put("subject", message.getSubject());
        body.put("text", message.getText());
        if (message.getHtml() != null) body.put("html", message.getHtml());
        String replyTo = message.getReplyTo() != null ? message.getReplyTo() : settings.getReplyTo();
        if (replyTo != null && !replyTo.isBlank()) body.put("reply_to", replyTo);

        return webClient.post()
                .uri("/emails")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10));
    }
}
