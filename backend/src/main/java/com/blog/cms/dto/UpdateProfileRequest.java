package com.blog.cms.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank
    @Email
    private String email;

    private String name;
    private String avatarUrl;
}
