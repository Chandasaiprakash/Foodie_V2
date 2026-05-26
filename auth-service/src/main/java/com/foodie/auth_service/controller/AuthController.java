package com.foodie.auth_service.controller;

import com.foodie.auth_service.dto.*;
import feign.FeignException;
import com.foodie.auth_service.resilience.ResilientUserClient;
import com.foodie.auth_service.security.JwtService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Auth controller — now using {@link ResilientUserClient} instead of the raw
 * Feign interface.  All calls to user-service are protected by:
 *   - CircuitBreaker (stops hammering a down user-service)
 *   - Bulkhead       (caps concurrent in-flight requests)
 *   - Retry          (handles transient connectivity blips)
 *
 * /login and /register are also protected by a RateLimiter so a burst of
 * auth requests (e.g., credential-stuffing or a frontend retry loop) cannot
 * starve other requests or overwhelm user-service.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final ResilientUserClient userClient;   // ← resilient wrapper
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${user-service.internal-secret}")
    private String internalSecret;

    // ── /register ────────────────────────────────────────────────────────────

    @PostMapping("/register")
    @RateLimiter(name = "authEndpoint", fallbackMethod = "rateLimitFallback")
    public AuthResponse register(@RequestBody RegisterRequest req) {
        UserAuthDetails createdUser;
        try {
            createdUser = userClient.createUser(req);
        } catch (FeignException.Conflict e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists.");
        } catch (ResilientUserClient.UserServiceUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }

        String token = jwtService.generateToken(
                createdUser.id(),
                createdUser.email(),
                createdUser.role(),
                createdUser.username(),
                createdUser.phoneNumber(),
                1000L * 60 * 60 * 24
        );

        return new AuthResponse(
                token,
                createdUser.id(),
                createdUser.email(),
                createdUser.role(),
                createdUser.username(),
                createdUser.phoneNumber()
        );
    }

    // ── /login ────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @RateLimiter(name = "authEndpoint", fallbackMethod = "rateLimitFallback")
    public AuthResponse login(@RequestBody LoginRequest req) {
        UserAuthDetails userDetails;
        try {
            userDetails = userClient.getUserByEmail(req.getEmail(), internalSecret);
        } catch (ResilientUserClient.UserServiceUnavailableException e) {
            // Circuit open or bulkhead full — return 503, NOT 401,
            // so the client knows to retry rather than thinking credentials failed
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (Exception e) {
            // Feign 404 = user not found → map to generic "Invalid credentials"
            // to prevent email enumeration
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        if (!passwordEncoder.matches(req.getPassword(), userDetails.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtService.generateToken(
                userDetails.id(),
                userDetails.email(),
                userDetails.role(),
                userDetails.username(),
                userDetails.phoneNumber(),
                1000L * 60 * 60 * 24
        );

        return new AuthResponse(
                token,
                userDetails.id(),
                userDetails.email(),
                userDetails.role(),
                userDetails.username(),
                userDetails.phoneNumber()
        );
    }

    // ── /me ───────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    public MeResponse me(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtService.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or expired token");
        }

        Claims claims = jwtService.getClaims(token);
        return new MeResponse(
                Long.parseLong(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("role", String.class),
                claims.get("username", String.class),
                claims.get("phoneNumber", String.class)
        );
    }

    // ── Fallbacks ─────────────────────────────────────────────────────────────

    /**
     * RateLimiter fallback — fires when /login or /register exceed the
     * configured limit (20 req/sec).  Returns HTTP 429 Too Many Requests.
     */
    public AuthResponse rateLimitFallback(
            Object ignored, // the request body parameter
            io.github.resilience4j.ratelimiter.RequestNotPermitted ex) {
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many auth requests. Please slow down.");
    }

    public record MeResponse(Long id, String email, String role,
                              String username, String phoneNumber) {}
}
