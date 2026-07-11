package com.blog.cms.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("readers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Reader {

    @Id
    private Long id;
    private String handle;
    private String displayName;
    private String email;
    private String avatarUrl;
    private LocalDateTime createdAt;
}
