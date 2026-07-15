package com.blog.cms.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("site_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteSettings {

    @Id
    private Long id;
    private String siteName;
    private boolean setupCompleted;
    private String theme;
    private String contrast;
    private String font;
    private String accentColor;
}
