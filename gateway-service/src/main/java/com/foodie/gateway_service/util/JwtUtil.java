package com.foodie.gateway_service.util;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;

@Component
public class JwtUtil {
    private final Key key = Keys.hmacShaKeyFor("super-secret-key-change-me-super-secret-key".getBytes());

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    public Claims getClaims(String token) {
        return Jwts.parser()
                .setSigningKey(key) // your secret key
                .parseClaimsJws(token)
                .getBody();
    }
}

