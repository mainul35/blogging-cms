package com.blog.cms.repository;

import com.blog.cms.model.Reader;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ReaderRepository extends ReactiveCrudRepository<Reader, Long> {
    Mono<Reader> findByHandle(String handle);
    Mono<Boolean> existsByHandle(String handle);
}
