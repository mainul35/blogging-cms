package com.blog.cms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, Object> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory factory, ObjectMapper objectMapper) {
        // Derive a copy of Spring Boot's shared ObjectMapper — keeps JavaTimeModule
        // registered (so LocalDateTime fields like PostResponse's serialize correctly)
        // — but with default typing turned on. GenericJackson2JsonRedisSerializer needs
        // that to embed type info ("@class") in the cached JSON; without it, cache reads
        // deserialize to a raw LinkedHashMap instead of the real DTO class, which blows
        // up with a ClassCastException at the call site. Using a copy rather than the
        // shared mapper directly matters: that mapper also serializes plain HTTP JSON
        // responses, where leaking "@class" metadata to API clients would be wrong.
        ObjectMapper redisObjectMapper = objectMapper.copy();
        redisObjectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL);

        RedisSerializationContext<String, Object> context = RedisSerializationContext
                .<String, Object>newSerializationContext(new StringRedisSerializer())
                .value(new GenericJackson2JsonRedisSerializer(redisObjectMapper))
                .build();
        return new ReactiveRedisTemplate<>(factory, context);
    }
}
