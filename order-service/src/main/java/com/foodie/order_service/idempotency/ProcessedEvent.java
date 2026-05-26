package com.foodie.order_service.idempotency;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Idempotency guard for Kafka consumers in order-service.
 *
 * DESIGN:
 *   event_id is the PRIMARY KEY (already unique by definition).
 *   The @Column(unique = true) annotation ensures Hibernate also generates
 *   a UNIQUE constraint in DDL — providing two layers of uniqueness enforcement:
 *     1. Primary key constraint  (DB-level)
 *     2. Unique column constraint (DDL-level — also generates a unique index)
 *
 * WHY TWO LAYERS?
 *   The PK alone suffices at runtime, but the explicit @Column(unique=true)
 *   makes the intent self-documenting and ensures the unique index is present
 *   even if the PK implementation is changed in future.
 *
 * ATOMICITY:
 *   IdempotencyService.claim() uses REQUIRES_NEW propagation and saveAndFlush().
 *   If two threads race, exactly one INSERT succeeds; the other gets
 *   DataIntegrityViolationException and returns false — skipping processing.
 *
 * TTL / CLEANUP:
 *   expires_at is stored so that IdempotencyCleanupScheduler can delete
 *   rows older than the retention window (default 7 days) without a full
 *   table scan. The idx_processed_events_expires_at index makes this O(log n).
 *
 *   Why keep records at all for 7 days?
 *   Kafka's consumer group offset commit can lag by minutes in crash-recovery
 *   scenarios. Deleting idempotency rows too early re-opens the duplicate window.
 *   7 days is safe for any realistic broker retention + retry configuration.
 */
@Entity
@Table(
    name = "processed_events",
    indexes = {
        @Index(name = "idx_processed_events_id",      columnList = "event_id",   unique = true),
        @Index(name = "idx_processed_events_expires",  columnList = "expires_at", unique = false)
    }
)
public class ProcessedEvent {

    /** Default retention: 7 days. Adjust via IdempotencyCleanupScheduler. */
    public static final long DEFAULT_TTL_DAYS = 7;

    @Id
    @Column(name = "event_id", nullable = false, unique = true, length = 255)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    /** Absolute expiry timestamp used by the cleanup job. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(String eventId) {
        this.eventId     = eventId;
        this.processedAt = Instant.now();
        this.expiresAt   = processedAt.plusSeconds(DEFAULT_TTL_DAYS * 86_400);
    }

    public String  getEventId()     { return eventId; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getExpiresAt()   { return expiresAt; }
}
