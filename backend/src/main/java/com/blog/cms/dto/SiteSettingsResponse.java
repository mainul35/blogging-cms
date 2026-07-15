package com.blog.cms.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteSettingsResponse {
    private String siteName;
    private String theme;
    private String contrast;
    private String font;
    private String accentColor;
}
