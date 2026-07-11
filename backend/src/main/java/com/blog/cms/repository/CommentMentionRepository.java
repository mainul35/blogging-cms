package com.blog.cms.repository;

import com.blog.cms.model.CommentMention;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface CommentMentionRepository extends ReactiveCrudRepository<CommentMention, Long> {

    Flux<CommentMention> findAllByCommentId(Long commentId);
}
