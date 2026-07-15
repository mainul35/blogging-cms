package com.blog.cms.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("mail_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MailSettings {

    @Id
    private Long id;
    private String provider;
    private String fromAddress;
    private String replyTo;
    private String smtpHost;
    private int smtpPort;
    private String smtpUsername;
    private String smtpPassword;
    private boolean smtpAuth;
    private boolean smtpStarttls;
    private String resendApiKey;
    private String sendgridApiKey;
}
