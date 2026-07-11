package com.blog.cms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OAuthLoginRequest {

    @NotBlank
    private String provider;        // "GOOGLE" | "GITHUB"

    @NotBlank
    private String providerUserId;

    @NotBlank
    private String email;

    @NotBlank
    private String displayName;

    private String avatarUrl;
}
