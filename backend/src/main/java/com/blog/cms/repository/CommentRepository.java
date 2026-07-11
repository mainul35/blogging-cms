package com.blog.cms.repository;

import com.blog.cms.model.Comment;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface CommentRepository extends ReactiveCrudRepository<Comment, Long> {

    Flux<Comment> findAllByPostIdOrderByCreatedAtAsc(Long postId);
}
