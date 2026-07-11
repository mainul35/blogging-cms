package com.blog.cms.repository;

import com.blog.cms.model.Post;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PostRepository extends ReactiveCrudRepository<Post, Long> {

    Mono<Post> findBySlug(String slug);

    Flux<Post> findAllByStatus(String status);

    Flux<Post> findAllByCategoryId(Long categoryId);

    Mono<Long> countByStatus(String status);

    // Used before deleting an orphaned upload — never remove a file another post still references.
    @Query("SELECT COUNT(*) FROM posts WHERE id != :excludeId AND (content LIKE CONCAT('%', :filename, '%') OR cover_image_url LIKE CONCAT('%', :filename, '%'))")
    Mono<Long> countOtherPostsReferencingFile(Long excludeId, String filename);
}
