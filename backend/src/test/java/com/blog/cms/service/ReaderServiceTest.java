package com.blog.cms.service;

import com.blog.cms.dto.OAuthLoginRequest;
import com.blog.cms.model.Reader;
import com.blog.cms.model.ReaderOAuthIdentity;
import com.blog.cms.repository.ReaderOAuthIdentityRepository;
import com.blog.cms.repository.ReaderRepository;
import com.blog.cms.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReaderServiceTest {

    private static final String INTERNAL_SECRET = "test-internal-secret";

    @Mock private ReaderRepository readerRepository;
    @Mock private ReaderOAuthIdentityRepository readerOAuthIdentityRepository;
    @Mock private JwtUtil jwtUtil;

    private ReaderService readerService;

    @BeforeEach
    void setUp() {
        readerService = new ReaderService(readerRepository, readerOAuthIdentityRepository, jwtUtil);
        ReflectionTestUtils.setField(readerService, "internalAuthSecret", INTERNAL_SECRET);
    }

    private OAuthLoginRequest request() {
        OAuthLoginRequest r = new OAuthLoginRequest();
        r.setProvider("GOOGLE");
        r.setProviderUserId("google-123");
        r.setEmail("jane@example.com");
        r.setDisplayName("Jane Doe");
        r.setAvatarUrl("https://example.com/avatar.png");
        return r;
    }

    // ---- secret gating ----

    @Test
    void oauthLogin_wrongSecret_isForbidden_neverTouchesRepositories() {
        StepVerifier.create(readerService.oauthLogin("wrong-secret", request()))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403))
                .verify();

        verify(readerOAuthIdentityRepository, never()).findByProviderAndProviderUserId(any(), any());
    }

    @Test
    void oauthLogin_nullSecret_isForbidden() {
        StepVerifier.create(readerService.oauthLogin(null, request()))
                .expectErrorSatisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode().value())
                        .isEqualTo(403))
                .verify();
    }

    // ---- existing identity ----

    @Test
    void oauthLogin_existingIdentity_returnsExistingReaderWithoutCreatingANewOne() {
        ReaderOAuthIdentity identity = ReaderOAuthIdentity.builder()
                .id(1L).readerId(50L).provider("GOOGLE").providerUserId("google-123").build();
        Reader existingReader = Reader.builder().id(50L).handle("jane_doe").displayName("Jane Doe")
                .email("jane@example.com").build();

        when(readerOAuthIdentityRepository.findByProviderAndProviderUserId("GOOGLE", "google-123"))
                .thenReturn(Mono.just(identity));
        when(readerRepository.findById(50L)).thenReturn(Mono.just(existingReader));
        when(jwtUtil.generateToken("jane_doe", "READER")).thenReturn("reader-jwt");
        // switchIfEmpty(createReader(request)) is constructed eagerly (same
        // gotcha as PostService/CommentService's switchIfEmpty chains) --
        // createReader's generateUniqueHandle -> attemptHandle calls
        // existsByHandle as part of just building that fallback Mono, even
        // though it's never actually subscribed to since the identity lookup
        // above succeeds. Needs a stub regardless.
        when(readerRepository.existsByHandle(anyString())).thenReturn(Mono.just(false));

        StepVerifier.create(readerService.oauthLogin(INTERNAL_SECRET, request()))
                .assertNext(r -> {
                    assertThat(r.getToken()).isEqualTo("reader-jwt");
                    assertThat(r.getHandle()).isEqualTo("jane_doe");
                })
                .verifyComplete();

        verify(readerRepository, never()).save(any());
    }

    // ---- new identity: creates reader + oauth identity ----

    @Test
    void oauthLogin_newIdentity_createsReaderWithSlugifiedHandle() {
        when(readerOAuthIdentityRepository.findByProviderAndProviderUserId("GOOGLE", "google-123"))
                .thenReturn(Mono.empty());
        when(readerRepository.existsByHandle("jane_doe")).thenReturn(Mono.just(false));
        when(readerRepository.save(any(Reader.class))).thenAnswer(inv -> {
            Reader r = inv.getArgument(0);
            r.setId(60L);
            return Mono.just(r);
        });
        when(readerOAuthIdentityRepository.save(any(ReaderOAuthIdentity.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(jwtUtil.generateToken("jane_doe", "READER")).thenReturn("reader-jwt");

        StepVerifier.create(readerService.oauthLogin(INTERNAL_SECRET, request()))
                .assertNext(r -> assertThat(r.getHandle()).isEqualTo("jane_doe"))
                .verifyComplete();

        verify(readerRepository).save(argThatReader(r -> "jane_doe".equals(r.getHandle())));
        verify(readerOAuthIdentityRepository).save(argThatIdentity(
                i -> i.getReaderId().equals(60L) && "GOOGLE".equals(i.getProvider())
                        && "google-123".equals(i.getProviderUserId())));
    }

    @Test
    void oauthLogin_handleCollision_appendsNumericSuffixUntilUnique() {
        when(readerOAuthIdentityRepository.findByProviderAndProviderUserId(any(), any())).thenReturn(Mono.empty());
        // "jane_doe" and "jane_doe1" are taken; "jane_doe2" is free.
        when(readerRepository.existsByHandle("jane_doe")).thenReturn(Mono.just(true));
        when(readerRepository.existsByHandle("jane_doe1")).thenReturn(Mono.just(true));
        when(readerRepository.existsByHandle("jane_doe2")).thenReturn(Mono.just(false));
        when(readerRepository.save(any(Reader.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(readerOAuthIdentityRepository.save(any(ReaderOAuthIdentity.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(jwtUtil.generateToken(anyString(), any())).thenReturn("jwt");

        StepVerifier.create(readerService.oauthLogin(INTERNAL_SECRET, request()))
                .assertNext(r -> assertThat(r.getHandle()).isEqualTo("jane_doe2"))
                .verifyComplete();
    }

    @Test
    void oauthLogin_displayNameWithNoAlphanumericChars_fallsBackToGenericHandle() {
        OAuthLoginRequest req = request();
        req.setDisplayName("😀😀😀"); // slugifies to blank after stripping non [a-z0-9_]

        when(readerOAuthIdentityRepository.findByProviderAndProviderUserId(any(), any())).thenReturn(Mono.empty());
        when(readerRepository.existsByHandle("reader")).thenReturn(Mono.just(false));
        when(readerRepository.save(any(Reader.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(readerOAuthIdentityRepository.save(any(ReaderOAuthIdentity.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(jwtUtil.generateToken(anyString(), any())).thenReturn("jwt");

        StepVerifier.create(readerService.oauthLogin(INTERNAL_SECRET, req))
                .assertNext(r -> assertThat(r.getHandle()).isEqualTo("reader"))
                .verifyComplete();
    }

    private static Reader argThatReader(java.util.function.Predicate<Reader> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }

    private static ReaderOAuthIdentity argThatIdentity(java.util.function.Predicate<ReaderOAuthIdentity> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
