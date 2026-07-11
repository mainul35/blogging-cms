package com.blog.cms.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReaderAuthResponse {
    private String token;
    private String handle;
    private String displayName;
    private String email;
    private String avatarUrl;
}
