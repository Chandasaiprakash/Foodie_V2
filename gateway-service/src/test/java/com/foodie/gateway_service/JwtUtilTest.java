package com.foodie.gateway_service;

import com.foodie.gateway_service.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String TEST_SECRET = "super-secret-key-change-me-super-secret-key";

    private JwtUtil jwtUtil;
    private final Key key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET);
    }

    private String buildToken(String email, String role, long expiryMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject("1")
                .claim("email", email)
                .claim("role", role)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expiryMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void validateToken_returnsTrue_forValidToken() {
        String token = buildToken("user@example.com", "CUSTOMER", 60_000L);
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_returnsFalse_forExpiredToken() throws InterruptedException {
        String token = buildToken("user@example.com", "CUSTOMER", 1L);
        Thread.sleep(10);
        assertThat(jwtUtil.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_returnsFalse_forMalformedToken() {
        assertThat(jwtUtil.validateToken("not.a.valid.jwt")).isFalse();
    }

    @Test
    void validateToken_returnsFalse_forEmptyToken() {
        assertThat(jwtUtil.validateToken("")).isFalse();
    }

    @Test
    void getClaims_extractsEmailClaim() {
        String token = buildToken("gateway@example.com", "ADMIN", 60_000L);
        Claims claims = jwtUtil.getClaims(token);
        assertThat(claims.get("email", String.class)).isEqualTo("gateway@example.com");
    }

    @Test
    void getClaims_extractsSubjectAsUserId() {
        String token = buildToken("user@example.com", "CUSTOMER", 60_000L);
        Claims claims = jwtUtil.getClaims(token);
        assertThat(claims.getSubject()).isEqualTo("1");
    }

    @Test
    void extractAllClaims_returnsAllClaims() {
        String token = buildToken("test@example.com", "CUSTOMER", 60_000L);
        Claims claims = jwtUtil.extractAllClaims(token);
        assertThat(claims.get("role", String.class)).isEqualTo("CUSTOMER");
    }
}
