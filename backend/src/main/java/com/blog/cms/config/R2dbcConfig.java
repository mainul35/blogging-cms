package com.blog.cms.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;

@Configuration
@EnableR2dbcAuditing
public class R2dbcConfig {
    // Spring Boot auto-configures the connection pool from application.yml.
    // This class enables @CreatedDate / @LastModifiedDate auditing on entities.
}
