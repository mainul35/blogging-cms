package com.blog.cms.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {
    private String email;
    private String name;
    private String avatarUrl;
    private String role;
}
