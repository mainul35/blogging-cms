package com.blog.cms.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("comment_mentions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentMention {

    @Id
    private Long id;
    private Long commentId;
    private Long mentionedUserId;
}
