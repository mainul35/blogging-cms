package com.blog.cms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SiteSettingsRequest {

    @NotBlank
    @Size(max = 100)
    private String siteName;

    @NotBlank
    @Pattern(regexp = "light|dark|system", message = "theme must be light, dark, or system")
    private String theme;

    @NotBlank
    @Pattern(regexp = "normal|high", message = "contrast must be normal or high")
    private String contrast;

    @NotBlank
    @Pattern(regexp = "inter|serif|mono", message = "font must be inter, serif, or mono")
    private String font;

    @NotBlank
    @Pattern(regexp = "blue|green|purple|red|orange|pink", message = "accentColor must be one of the supported presets")
    private String accentColor;
}
