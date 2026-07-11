package com.blog.cms.service;

import com.blog.cms.dto.OAuthLoginRequest;
import com.blog.cms.dto.ReaderAuthResponse;
import com.blog.cms.model.Reader;
import com.blog.cms.model.ReaderOAuthIdentity;
import com.blog.cms.repository.ReaderOAuthIdentityRepository;
import com.blog.cms.repository.ReaderRepository;
import com.blog.cms.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReaderService {

    private final ReaderRepository readerRepository;
    private final ReaderOAuthIdentityRepository readerOAuthIdentityRepository;
    private final JwtUtil jwtUtil;

    @Value("${app.internal.auth-secret}")
    private String internalAuthSecret;

    public Mono<ReaderAuthResponse> oauthLogin(String providedSecret, OAuthLoginRequest request) {
        if (providedSecret == null || !internalAuthSecret.equals(providedSecret)) {
            log.warn("Reader OAuth bridge called with an invalid or missing internal secret");
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid internal secret"));
        }

        return readerOAuthIdentityRepository
                .findByProviderAndProviderUserId(request.getProvider(), request.getProviderUserId())
                .flatMap(identity -> readerRepository.findById(identity.getReaderId()))
                .switchIfEmpty(createReader(request))
                .map(this::toAuthResponse);
    }

    private Mono<Reader> createReader(OAuthLoginRequest request) {
        return generateUniqueHandle(request.getDisplayName())
                .flatMap(handle -> readerRepository.save(Reader.builder()
                        .handle(handle)
                        .displayName(request.getDisplayName())
                        .email(request.getEmail())
                        .avatarUrl(request.getAvatarUrl())
                        .createdAt(LocalDateTime.now())
                        .build()))
                .flatMap(saved -> readerOAuthIdentityRepository.save(ReaderOAuthIdentity.builder()
                                .readerId(saved.getId())
                                .provider(request.getProvider())
                                .providerUserId(request.getProviderUserId())
                                .build())
                        .thenReturn(saved));
    }

    // Handle charset must match CommentService.MENTION_PATTERN exactly
    // ([a-zA-Z0-9_]) or a generated handle could be un-@-mentionable.
    private Mono<String> generateUniqueHandle(String displayName) {
        String base = slugify(displayName);
        return attemptHandle(base, 0);
    }

    private Mono<String> attemptHandle(String base, int suffix) {
        String candidate = suffix == 0 ? base : base + suffix;
        return readerRepository.existsByHandle(candidate)
                .flatMap(exists -> exists
                        ? attemptHandle(base, suffix + 1)
                        : Mono.just(candidate));
    }

    private String slugify(String displayName) {
        String slug = displayName.toLowerCase().replaceAll("[^a-z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
        if (slug.isBlank()) slug = "reader";
        return slug.length() > 40 ? slug.substring(0, 40) : slug;
    }

    private ReaderAuthResponse toAuthResponse(Reader reader) {
        return ReaderAuthResponse.builder()
                .token(jwtUtil.generateToken(reader.getHandle(), "READER"))
                .handle(reader.getHandle())
                .displayName(reader.getDisplayName())
                .email(reader.getEmail())
                .avatarUrl(reader.getAvatarUrl())
                .build();
    }
}
