package com.foodie.payment_service.idempotency;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Idempotency guard for Kafka consumers in payment-service.
 *
 * Mirrors order-service ProcessedEvent — see that class for full design docs.
 *
 * KEY ADDITIONS vs original:
 *   - expires_at column with index for TTL-based cleanup
 *   - IdempotencyCleanupScheduler reads this column to purge old rows
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

    public static final long DEFAULT_TTL_DAYS = 7;

    @Id
    @Column(name = "event_id", nullable = false, unique = true, length = 255)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

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
