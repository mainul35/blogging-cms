package com.blog.cms.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.WebFilterChain;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private JwtUtil jwtUtil;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtUtil);
    }

    // WebFilterChain that captures whatever Authentication (if any) ends up
    // in the reactive Security context by the time downstream code runs --
    // the real way to observe that JwtAuthFilter's contextWrite() actually
    // took effect, rather than just checking the filter didn't throw.
    private WebFilterChain capturingChain(AtomicReference<Authentication> captured) {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .doOnNext(ctx -> captured.set(ctx.getAuthentication()))
                .then(); // Mono<Void> either way -- empty upstream (no context set) still completes empty
    }

    @Test
    void filter_validAdminToken_setsAuthenticationWithAdminRole() {
        when(jwtUtil.isTokenValid("valid-token")).thenReturn(true);
        when(jwtUtil.extractEmail("valid-token")).thenReturn("admin@blog.com");
        when(jwtUtil.extractRole("valid-token")).thenReturn("ADMIN");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .build());
        AtomicReference<Authentication> captured = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, capturingChain(captured))).verifyComplete();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getName()).isEqualTo("admin@blog.com");
        assertThat(captured.get().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void filter_validReaderToken_setsAuthenticationWithReaderRole() {
        when(jwtUtil.isTokenValid("reader-token")).thenReturn(true);
        when(jwtUtil.extractEmail("reader-token")).thenReturn("jane_doe");
        when(jwtUtil.extractRole("reader-token")).thenReturn("READER");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/posts/some-slug/comments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer reader-token")
                        .build());
        AtomicReference<Authentication> captured = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, capturingChain(captured))).verifyComplete();

        assertThat(captured.get().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_READER");
    }

    @Test
    void filter_noAuthorizationHeader_passesThroughWithoutSettingContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/posts").build());
        AtomicReference<Authentication> captured = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, capturingChain(captured))).verifyComplete();

        assertThat(captured.get()).isNull();
    }

    @Test
    void filter_headerWithoutBearerPrefix_passesThroughUntouched() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/posts")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                        .build());
        AtomicReference<Authentication> captured = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, capturingChain(captured))).verifyComplete();

        assertThat(captured.get()).isNull();
        verify(jwtUtil, never()).isTokenValid(any());
    }

    @Test
    void filter_invalidOrExpiredToken_passesThroughWithoutSettingContext() {
        when(jwtUtil.isTokenValid("bad-token")).thenReturn(false);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/stats")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                        .build());
        AtomicReference<Authentication> captured = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, capturingChain(captured))).verifyComplete();

        assertThat(captured.get()).isNull();
        verify(jwtUtil, never()).extractEmail(any());
    }
}
