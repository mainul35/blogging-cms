package com.blog.cms.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentResponse {

    private Long id;
    private String body;
    private String authorName;
    private String authorType;       // "ADMIN" | "READER" | "GUEST"
    private String authorHandle;     // reader handle or admin username; null for guests
    private String authorAvatarUrl;
    private Long parentId;
    private List<String> mentionedUsernames;   // admin usernames and reader handles, uniformly
    private LocalDateTime createdAt;

    // Populated by the admin endpoint only
    private String postTitle;
    private String postSlug;

    // Populated server-side when building the threaded tree (public API)
    @Builder.Default
    private List<CommentResponse> replies = new ArrayList<>();
}
