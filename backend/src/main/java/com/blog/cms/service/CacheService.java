package com.blog.cms.service;

import com.blog.cms.dto.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class CacheService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String POST_PREFIX = "post:";

    public Mono<PostResponse> getPost(String slug) {
        return redisTemplate.opsForValue()
                .get(POST_PREFIX + slug)
                .cast(PostResponse.class);
    }

    public Mono<Boolean> cachePost(String slug, PostResponse response) {
        return redisTemplate.opsForValue()
                .set(POST_PREFIX + slug, response, TTL);
    }

    public Mono<Void> evictPost(String slug) {
        return redisTemplate.delete(POST_PREFIX + slug).then();
    }
}
