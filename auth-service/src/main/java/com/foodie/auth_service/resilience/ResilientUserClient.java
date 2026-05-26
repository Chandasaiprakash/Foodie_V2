package com.foodie.auth_service.resilience;

import com.foodie.auth_service.client.UserClient;
import com.foodie.auth_service.dto.RegisterRequest;
import com.foodie.auth_service.dto.UserAuthDetails;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resilient wrapper around the {@link UserClient} Feign interface in auth-service.
 *
 * <p>auth-service is in the hot path for every single request: if user-service
 * is slow, every login attempt hangs.  This wrapper ensures:
 * <ul>
 *   <li><b>Bulkhead</b> caps concurrent Feign calls so a slow user-service
 *       cannot exhaust auth-service's thread pool and bring the whole service down.</li>
 *   <li><b>CircuitBreaker</b> opens after 50% failure rate, returning a fast
 *       failure to callers rather than waiting for timeouts to pile up.</li>
 *   <li><b>Retry</b> handles transient connectivity glitches (2 attempts,
 *       only on network-level exceptions — not on 401/404 which are expected
 *       business errors that should not be retried).</li>
 * </ul>
 *
 * <p>Fallback behaviour:
 * <ul>
 *   <li>{@link #getUserByEmailFallback} — throws {@link UserServiceUnavailableException}
 *       so the auth controller can return HTTP 503 with a clear error body rather
 *       than a confusing NullPointerException stack trace.</li>
 *   <li>{@link #createUserFallback} — same pattern; registration fails fast
 *       with 503 rather than timing out.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResilientUserClient {

    private final UserClient userClient;

    /** Runtime exception thrown when user-service circuit is open. */
    public static class UserServiceUnavailableException extends RuntimeException {
        public UserServiceUnavailableException(String msg) { super(msg); }
        public UserServiceUnavailableException(String msg, Throwable cause) { super(msg, cause); }
    }

    // ── getUserByEmail ───────────────────────────────────────────────────────

    @Bulkhead(name = "userServiceClient", fallbackMethod = "getUserByEmailFallback")
    @CircuitBreaker(name = "userServiceClient", fallbackMethod = "getUserByEmailFallback")
    @Retry(name = "userServiceClient", fallbackMethod = "getUserByEmailFallback")
    public UserAuthDetails getUserByEmail(String email, String internalSecret) {
        return userClient.getUserByEmailForAuth(email, internalSecret);
    }

    public UserAuthDetails getUserByEmailFallback(
            String email, String internalSecret,
            io.github.resilience4j.bulkhead.BulkheadFullException ex) {
        log.warn("Bulkhead full — userServiceClient.getUserByEmail email={}", email);
        throw new UserServiceUnavailableException(
                "User service is currently overloaded. Please try again.", ex);
    }

    public UserAuthDetails getUserByEmailFallback(
            String email, String internalSecret,
            io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
        log.warn("Circuit OPEN — userServiceClient.getUserByEmail email={}", email);
        throw new UserServiceUnavailableException(
                "User service is temporarily unavailable. Please try again shortly.", ex);
    }

    public UserAuthDetails getUserByEmailFallback(
            String email, String internalSecret, Throwable ex) {
        log.warn("userServiceClient.getUserByEmail failed email={}: {}", email, ex.getMessage());
        throw new UserServiceUnavailableException(
                "Unable to reach user service. Please try again.", ex);
    }

    // ── createUser ───────────────────────────────────────────────────────────

    @Bulkhead(name = "userServiceClient", fallbackMethod = "createUserFallback")
    @CircuitBreaker(name = "userServiceClient", fallbackMethod = "createUserFallback")
    @Retry(name = "userServiceClient", fallbackMethod = "createUserFallback")
    public UserAuthDetails createUser(RegisterRequest request) {
        return userClient.createUser(request);
    }

    public UserAuthDetails createUserFallback(
            RegisterRequest request,
            io.github.resilience4j.bulkhead.BulkheadFullException ex) {
        log.warn("Bulkhead full — userServiceClient.createUser");
        throw new UserServiceUnavailableException(
                "User service is currently overloaded. Registration unavailable.", ex);
    }

    public UserAuthDetails createUserFallback(
            RegisterRequest request,
            io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
        log.warn("Circuit OPEN — userServiceClient.createUser");
        throw new UserServiceUnavailableException(
                "User service is temporarily unavailable. Registration unavailable.", ex);
    }

    public UserAuthDetails createUserFallback(RegisterRequest request, Throwable ex) {
        log.warn("userServiceClient.createUser failed: {}", ex.getMessage());
        throw new UserServiceUnavailableException(
                "Unable to reach user service. Please try again.", ex);
    }
}
