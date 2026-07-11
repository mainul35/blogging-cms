package com.blog.cms.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("newsletter_subscribers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterSubscriber {

    @Id
    private Long id;
    private String email;
    private boolean confirmed;
    private String token;         // UUID for double opt-in confirmation link
    private LocalDateTime subscribedAt;
}
