package com.foodie.auth_service;

import com.foodie.auth_service.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
    }

    private String generateToken(String email, String role) {
        return jwtService.generateToken(42L, email, role, "testuser", "9876543210", 60_000L);
    }

    @Test
    void generateToken_producesNonBlankToken() {
        String token = generateToken("user@example.com", "CUSTOMER");
        assertThat(token).isNotBlank();
    }

    @Test
    void validateToken_returnsTrue_forValidToken() {
        String token = generateToken("user@example.com", "CUSTOMER");
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_returnsFalse_forGarbageToken() {
        assertThat(jwtService.validateToken("this.is.garbage")).isFalse();
    }

    @Test
    void validateToken_returnsFalse_forExpiredToken() throws InterruptedException {
        // generate a token that expires in 1ms
        String token = jwtService.generateToken(1L, "user@example.com", "CUSTOMER", "user", "123", 1L);
        Thread.sleep(10);
        assertThat(jwtService.validateToken(token)).isFalse();
    }

    @Test
    void getClaims_extractsEmailCorrectly() {
        String token = generateToken("user@example.com", "CUSTOMER");
        Claims claims = jwtService.getClaims(token);
        assertThat(claims.get("email", String.class)).isEqualTo("user@example.com");
    }

    @Test
    void getClaims_extractsRoleCorrectly() {
        String token = generateToken("admin@example.com", "ADMIN");
        Claims claims = jwtService.getClaims(token);
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void getClaims_extractsSubjectAsUserId() {
        String token = jwtService.generateToken(99L, "user@example.com", "CUSTOMER", "user", "000", 60_000L);
        Claims claims = jwtService.getClaims(token);
        assertThat(claims.getSubject()).isEqualTo("99");
    }

    @Test
    void getClaims_extractsUsernameAndPhone() {
        String token = jwtService.generateToken(1L, "u@x.com", "CUSTOMER", "johnDoe", "1112223333", 60_000L);
        Claims claims = jwtService.getClaims(token);
        assertThat(claims.get("username", String.class)).isEqualTo("johnDoe");
        assertThat(claims.get("phoneNumber", String.class)).isEqualTo("1112223333");
    }

    @Test
    void validateToken_returnsFalse_forEmptyString() {
        assertThat(jwtService.validateToken("")).isFalse();
    }
}
