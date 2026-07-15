package com.blog.cms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Secret fields (smtpPassword, resendApiKey, sendgridApiKey) are nullable/blank
// on purpose -- the form never re-displays a saved secret, so "left blank"
// means "keep the existing value", not "clear it". See MailSettingsService.
@Data
public class MailSettingsRequest {

    @NotBlank
    private String provider;

    @NotBlank
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
