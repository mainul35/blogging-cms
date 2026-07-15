package com.blog.cms.dto;

import lombok.Builder;
import lombok.Data;

// Never carries actual secret values back to the client -- only whether one
// is currently set, so the form can show "already configured" without ever
// re-exposing a saved password/API key over the wire.
@Data
@Builder
public class MailSettingsResponse {
    private String provider;
    private String fromAddress;
    private String replyTo;
    private String smtpHost;
    private int smtpPort;
    private String smtpUsername;
    private boolean smtpAuth;
    private boolean smtpStarttls;
    private boolean hasSmtpPassword;
    private boolean hasResendApiKey;
    private boolean hasSendgridApiKey;
}
