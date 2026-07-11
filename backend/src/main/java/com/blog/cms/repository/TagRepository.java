package com.blog.cms.repository;

import com.blog.cms.model.Tag;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface TagRepository extends ReactiveCrudRepository<Tag, Long> {

    Mono<Tag> findBySlug(String slug);

    Flux<Tag> findAllByIdIn(List<Long> ids);
}
