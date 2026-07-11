package com.blog.cms.repository;

import com.blog.cms.model.ReaderOAuthIdentity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ReaderOAuthIdentityRepository extends ReactiveCrudRepository<ReaderOAuthIdentity, Long> {
    Mono<ReaderOAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);
}
