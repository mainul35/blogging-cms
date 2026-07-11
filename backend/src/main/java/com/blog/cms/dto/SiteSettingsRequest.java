package com.blog.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteSettingsRequest {

    @NotBlank
    @Size(max = 100)
    private String siteName;
}
