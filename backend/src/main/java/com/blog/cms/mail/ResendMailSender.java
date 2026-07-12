package com.blog.cms.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "resend")
public class ResendMailSender implements MailSender {

    private final WebClient webClient;
    private final String from;
    private final String defaultReplyTo;

    public ResendMailSender(@Value("${app.mail.resend.api-key}") String apiKey,
                             @Value("${app.mail.from}") String from,
                             @Value("${app.mail.reply-to:}") String defaultReplyTo) {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
        this.from = from;
        this.defaultReplyTo = defaultReplyTo;
    }

    @Override
    public Mono<Void> send(MailMessage message) {
        Map<String, Object> body = new HashMap<>();
        body.put("from", from);
        body.put("to", message.getTo());
        body.put("subject", message.getSubject());
        body.put("text", message.getText());
        if (message.getHtml() != null) body.put("html", message.getHtml());
        String replyTo = message.getReplyTo() != null ? message.getReplyTo() : defaultReplyTo;
        if (replyTo != null && !replyTo.isBlank()) body.put("reply_to", replyTo);

        return webClient.post()
                .uri("/emails")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .timeout(Duration.ofSeconds(10));
    }
}
