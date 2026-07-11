package com.blog.cms.repository;

import com.blog.cms.model.NewsletterSubscriber;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface NewsletterRepository extends ReactiveCrudRepository<NewsletterSubscriber, Long> {

    Mono<NewsletterSubscriber> findByToken(String token);

    Mono<NewsletterSubscriber> findByEmail(String email);

    Mono<Boolean> existsByEmail(String email);

    Flux<NewsletterSubscriber> findAllByConfirmed(boolean confirmed);
}
