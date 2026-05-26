package com.foodie.auth_service.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    // In dev: keep secret length >= 32 bytes. In prod, store in env/secret manager.
    private final Key key = Keys.hmacShaKeyFor("super-secret-key-change-me-super-secret-key".getBytes());

    // ✅ Generate token with userId, email & role
    public String generateToken(Long userId, String email, String role,String username,String phoneNumber, long ttlMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(userId.toString()) // userId in sub
                .claim("email", email) // email claim
                .claim("role", role)// role claim
                .claim("username", username)
                .claim("phoneNumber", phoneNumber)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttlMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ✅ Parse claims (for validation & extraction)
    public Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
    public Jws<Claims> parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }



}
