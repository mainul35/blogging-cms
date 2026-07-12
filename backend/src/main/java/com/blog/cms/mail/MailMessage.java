package com.blog.cms.mail;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MailMessage {
    private String to;
    private String subject;
    private String text;
    private String html;     // nullable — unused today, kept for future HTML-capable providers
    private String replyTo;  // nullable — falls back to app.mail.reply-to inside each sender
}
