package com.blog.cms.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "sendgrid")
public class SendGridMailSender implements MailSender {

    private final WebClient webClient;
    private final String from;
    private final String defaultReplyTo;

    public SendGridMailSender(@Value("${app.mail.sendgrid.api-key}") String apiKey,
                               @Value("${app.mail.from}") String from,
                               @Value("${app.mail.reply-to:}") String defaultReplyTo) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.sendgrid.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.from = from;
        this.defaultReplyTo = defaultReplyTo;
    }

    @Override
    public Mono<Void> send(MailMessage message) {
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text/plain", "value", message.getText()));
        if (message.getHtml() != null) {
            content.add(Map.of("type", "text/html", "value", message.getHtml()));
        }

        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", message.getTo())))),
                "from", Map.of("email", from),
                "subject", message.getSubject(),
                "content", content
        ));
        String replyTo = message.getReplyTo() != null ? message.getReplyTo() : defaultReplyTo;
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
