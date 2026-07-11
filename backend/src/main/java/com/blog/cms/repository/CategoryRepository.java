package com.blog.cms.repository;

import com.blog.cms.model.Category;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface CategoryRepository extends ReactiveCrudRepository<Category, Long> {

    Mono<Category> findBySlug(String slug);
}
