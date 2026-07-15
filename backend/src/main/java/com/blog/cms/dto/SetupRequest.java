package com.blog.cms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetupRequest {
    @NotBlank
    @Size(max = 100)
    private String siteName;

    @NotBlank
    @Size(max = 50)
    private String adminName;

    @NotBlank
    @Email(message = "Must be a valid email address")
    private String adminEmail;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String adminPassword;

    // Optional -- omitted/null means "skip, keep the log-only default from
    // V11's seed row." Deliberately not cascade-validated (no @Valid) since
    // it's fine for the wizard to send nothing here at all.
    private MailSettingsRequest mailSettings;
}
