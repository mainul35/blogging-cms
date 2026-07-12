package com.blog.cms.mail;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Properties;

// Covers Gmail, any self-hosted mail server, and AWS SES / Mailgun / Postmark
// via their SMTP relay credentials -- no dedicated SDK needed for those three.
// Manually configured (not Spring's own spring.mail.* autoconfiguration) so
// this stays under the app.mail.* namespace like every other provider here.
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "smtp")
public class SmtpMailSender implements MailSender {

    private final JavaMailSenderImpl mailSender;
    private final String from;
    private final String defaultReplyTo;

    public SmtpMailSender(@Value("${app.mail.smtp.host}") String host,
                           @Value("${app.mail.smtp.port}") int port,
                           @Value("${app.mail.smtp.username}") String username,
                           @Value("${app.mail.smtp.password}") String password,
                           @Value("${app.mail.smtp.auth:true}") boolean auth,
                           @Value("${app.mail.smtp.starttls:true}") boolean starttls,
                           @Value("${app.mail.from}") String from,
                           @Value("${app.mail.reply-to:}") String defaultReplyTo) {
        this.mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", auth);
        props.put("mail.smtp.starttls.enable", starttls);
        this.from = from;
        this.defaultReplyTo = defaultReplyTo;
    }

    @Override
    public Mono<Void> send(MailMessage message) {
        // JavaMailSender.send() is blocking -- keep it off the WebFlux event
        // loop, same pattern already used by UploadService's file I/O.
        return Mono.fromRunnable(() -> {
                    MimeMessage mime = mailSender.createMimeMessage();
                    try {
                        MimeMessageHelper helper = new MimeMessageHelper(mime);
                        helper.setFrom(from);
                        helper.setTo(message.getTo());
                        helper.setSubject(message.getSubject());
                        helper.setText(message.getText());
                        String replyTo = message.getReplyTo() != null ? message.getReplyTo() : defaultReplyTo;
                        if (replyTo != null && !replyTo.isBlank()) helper.setReplyTo(replyTo);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    mailSender.send(mime);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
