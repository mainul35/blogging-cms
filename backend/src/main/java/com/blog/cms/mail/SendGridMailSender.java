package com.blog.cms.mail;

import com.blog.cms.model.MailSettings;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Not a Spring bean -- see SmtpMailSender for why (constructed per send() call
// from the current DB-stored MailSettings by MailSenderRouter).
public class SendGridMailSender {

    public static Mono<Void> send(MailMessage message, MailSettings settings) {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api.sendgrid.com")
                .defaultHeader("Authorization", "Bearer " + settings.getSendgridApiKey())
                .build();

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text/plain", "value", message.getText()));
        if (message.getHtml() != null) {
            content.add(Map.of("type", "text/html", "value", message.getHtml()));
        }

        Map<String, Object> body = new HashMap<>(Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", message.getTo())))),
                "from", Map.of("email", settings.getFromAddress()),
                "subject", message.getSubject(),
                "content", content
        ));
        String replyTo = message.getReplyTo() != null ? message.getReplyTo() : settings.getReplyTo();
        if (replyTo != null && !replyTo.isBlank()) {
            body.put("reply_to", Map.of("email", replyTo));
        }

        return webClient.post()
                .uri("/v3/mail/send")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10));
    }
}
