package com.blog.cms.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Real JwtUtil, no mocking -- it has no dependencies of its own, just two
// @Value-injected fields set here via ReflectionTestUtils.
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // HMAC-SHA needs a reasonably long key -- jjwt rejects anything too
        // short for the algorithm it picks based on key size.
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-that-is-long-enough-for-hmac-sha-256");
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", 3600_000L); // 1 hour
    }

    @Test
    void generateToken_roundTripsEmailAndRole() {
        String token = jwtUtil.generateToken("admin@blog.com", "ADMIN");

        assertThat(jwtUtil.extractEmail(token)).isEqualTo("admin@blog.com");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void generateToken_freshToken_isValid() {
        String token = jwtUtil.generateToken("admin@blog.com", "ADMIN");

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_expiredToken_isFalse() {
        ReflectionTestUtils.setField(jwtUtil, "expirationMs", -1000L); // already expired the instant it's issued
        String token = jwtUtil.generateToken("admin@blog.com", "ADMIN");

        assertThat(jwtUtil.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_malformedToken_isFalseNotAnException() {
        assertThat(jwtUtil.isTokenValid("not-a-real-jwt")).isFalse();
    }

    @Test
    void isTokenValid_tokenSignedWithDifferentSecret_isFalse() {
        JwtUtil otherIssuer = new JwtUtil();
        ReflectionTestUtils.setField(otherIssuer, "secret", "a-completely-different-secret-key-of-sufficient-length");
        ReflectionTestUtils.setField(otherIssuer, "expirationMs", 3600_000L);
        String tokenFromOtherIssuer = otherIssuer.generateToken("admin@blog.com", "ADMIN");

        assertThat(jwtUtil.isTokenValid(tokenFromOtherIssuer)).isFalse();
    }

    @Test
    void extractClaims_tokenSignedWithDifferentSecret_throwsRatherThanSilentlyTrusting() {
        JwtUtil otherIssuer = new JwtUtil();
        ReflectionTestUtils.setField(otherIssuer, "secret", "a-completely-different-secret-key-of-sufficient-length");
        ReflectionTestUtils.setField(otherIssuer, "expirationMs", 3600_000L);
        String tokenFromOtherIssuer = otherIssuer.generateToken("admin@blog.com", "ADMIN");

        // extractClaims (unlike isTokenValid) doesn't catch the verification
        // failure itself -- callers that skip the isTokenValid check first
        // would see this exception, not a quiet null/false.
        assertThatThrownBy(() -> jwtUtil.extractClaims(tokenFromOtherIssuer))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void extractClaims_readerToken_carriesReaderRole() {
        String token = jwtUtil.generateToken("jane_doe", "READER");

        Claims claims = jwtUtil.extractClaims(token);
        assertThat(claims.getSubject()).isEqualTo("jane_doe");
        assertThat(claims.get("role", String.class)).isEqualTo("READER");
    }
}
