package com.blog.cms.repository;

import com.blog.cms.model.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends ReactiveCrudRepository<User, Long> {

    // Case/whitespace-insensitive: a derived findByEmail did an exact `=`
    // match, which silently missed real accounts whenever the caller's input
    // casing (or a stray leading/trailing space from a form) differed from
    // however the address happened to be stored -- e.g. forgot-password
    // finding nothing and failing anti-enumeration-silently, with no error
    // anywhere, even though the email "matched" by eye.
    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(TRIM(:email))")
    Mono<User> findByEmail(String email);

    Mono<User> findByUsername(String username);

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE LOWER(email) = LOWER(TRIM(:email)))")
    Mono<Boolean> existsByEmail(String email);

    Mono<User> findFirstByRole(String role);

    Mono<User> findByResetToken(String resetToken);
}
