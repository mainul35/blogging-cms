package com.blog.cms.service;

import com.blog.cms.dto.PostResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock private ReactiveRedisTemplate<String, Object> redisTemplate;
    @Mock private ReactiveValueOperations<String, Object> valueOperations;

    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getPost_delegatesToPrefixedKey() {
        PostResponse response = PostResponse.builder().id(1L).slug("hello-world").build();
        when(valueOperations.get("post:hello-world")).thenReturn(Mono.just(response));

        StepVerifier.create(cacheService.getPost("hello-world"))
                .expectNext(response)
                .verifyComplete();
    }

    @Test
    void cachePost_writesWithPrefixedKeyAndTenMinuteTtl() {
        PostResponse response = PostResponse.builder().id(1L).slug("hello-world").build();
        when(valueOperations.set(eq("post:hello-world"), eq(response), eq(Duration.ofMinutes(10))))
                .thenReturn(Mono.just(true));

        StepVerifier.create(cacheService.cachePost("hello-world", response))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void evictPost_deletesPrefixedKey() {
        when(redisTemplate.delete("post:hello-world")).thenReturn(Mono.just(1L));

        StepVerifier.create(cacheService.evictPost("hello-world")).verifyComplete();

        verify(redisTemplate).delete("post:hello-world");
    }
}
