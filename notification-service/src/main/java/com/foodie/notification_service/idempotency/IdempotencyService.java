package com.foodie.notification_service.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Idempotency guard for notification-service using Redis SETNX.
 *
 * WHY REDIS INSTEAD OF A DATABASE?
 *   notification-service is stateless — it has no dedicated persistence store.
 *   Adding a MySQL or MongoDB just for idempotency is over-engineering here.
 *   Redis is already typically present in the stack (rate limiting, session cache)
 *   and provides atomic SETNX (SET if Not eXists) natively.
 *
 * HOW IT WORKS:
 *   redisTemplate.opsForValue().setIfAbsent(key, "1", TTL)
 *   - Returns Boolean.TRUE  → key did not exist → first time → process
 *   - Returns Boolean.FALSE → key already exists → duplicate → skip
 *   - The TTL is set atomically in the same command (SET NX EX) — no race window
 *     between the SET and EXPIRE.
 *
 * RACE CONDITION SAFETY:
 *   Redis is single-threaded for command execution. Two concurrent setIfAbsent
 *   calls for the same key: exactly one returns TRUE, the other returns FALSE.
 *   No application-level locking needed.
 *
 * TTL:
 *   24 hours is sufficient for notifications — if a duplicate arrives more than
 *   24 hours later, it's a new notification context, not a replay.
 *
 * FALLBACK:
 *   If Redis is unavailable, claim() defaults to true (process the event) and
 *   logs a warning. Notification duplicates are low-severity compared to silently
 *   dropping all notifications. Adjust this policy based on your SLA.
 *
 * PREREQUISITE:
 *   Add to notification-service pom.xml:
 *     <dependency>
 *       <groupId>org.springframework.boot</groupId>
 *       <artifactId>spring-boot-starter-data-redis</artifactId>
 *     </dependency>
 *
 *   Add to application.properties:
 *     spring.data.redis.host=${REDIS_HOST:localhost}
 *     spring.data.redis.port=${REDIS_PORT:6379}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "notif:idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    /**
     * Attempts to claim an event for notification processing.
     *
     * @param eventId unique identifier (e.g. "order-updated::uuid")
     * @return true  → first time seen, caller MUST send the notification
     *         false → duplicate, caller MUST skip
     */
    public boolean claim(String eventId) {
        String key = KEY_PREFIX + eventId;
        try {
            Boolean inserted = redisTemplate.opsForValue().setIfAbsent(key, "1", TTL);
            if (Boolean.TRUE.equals(inserted)) {
                log.debug("Notification idempotency claimed: {}", eventId);
                return true;
            } else {
                log.info("Duplicate notification suppressed (Redis SETNX): {}", eventId);
                return false;
            }
        } catch (Exception e) {
            // Redis unavailable — fail open (send the notification, risk a duplicate)
            // rather than fail closed (drop all notifications).
            log.warn("Redis unavailable for idempotency check on {}; processing anyway. Error: {}",
                     eventId, e.getMessage());
            return true;
        }
    }
}
