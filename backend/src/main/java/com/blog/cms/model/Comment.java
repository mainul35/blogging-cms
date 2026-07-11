package com.blog.cms.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    private Long id;
    private Long postId;
    private Long authorId;       // set only for admin-authored comments
    private Long readerId;       // set only for OAuth-signed-in reader comments
    private String authorName;
    private String authorEmail;
    private String body;
    private Long parentId;       // null for top-level; set for replies
    private LocalDateTime createdAt;
}
