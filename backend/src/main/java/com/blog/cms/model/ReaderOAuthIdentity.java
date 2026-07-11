package com.blog.cms.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("reader_oauth_identities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReaderOAuthIdentity {

    @Id
    private Long id;
    private Long readerId;
    private String provider;
    private String providerUserId;
}
