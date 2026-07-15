package com.blog.cms.mail;

import com.blog.cms.model.MailSettings;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Properties;

// Covers Gmail, any self-hosted mail server, and AWS SES / Mailgun / Postmark
// via their SMTP relay credentials -- no dedicated SDK needed for those three.
// Not a Spring bean: MailSenderRouter constructs one of these per send() call
// from the current DB-stored MailSettings, since the provider (and its
// credentials) can now change at runtime instead of being fixed at startup.
public class SmtpMailSender {

    public static Mono<Void> send(MailMessage message, MailSettings settings) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(settings.getSmtpHost());
        mailSender.setPort(settings.getSmtpPort());
        mailSender.setUsername(settings.getSmtpUsername());
        mailSender.setPassword(settings.getSmtpPassword());
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", settings.isSmtpAuth());
        props.put("mail.smtp.starttls.enable", settings.isSmtpStarttls());

        // JavaMailSender.send() is blocking -- keep it off the WebFlux event
        // loop, same pattern already used by UploadService's file I/O.
        return Mono.fromRunnable(() -> {
                    MimeMessage mime = mailSender.createMimeMessage();
                    try {
                        MimeMessageHelper helper = new MimeMessageHelper(mime);
                        helper.setFrom(settings.getFromAddress());
                        helper.setTo(message.getTo());
                        helper.setSubject(message.getSubject());
                        helper.setText(message.getText());
                        String replyTo = message.getReplyTo() != null ? message.getReplyTo() : settings.getReplyTo();
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
