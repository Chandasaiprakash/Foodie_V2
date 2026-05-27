package com.foodie.order_service.idempotency;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Idempotency guard for Kafka consumers in order-service.
 *
 * CRITICAL:
 * Spring Data JPA treats non-null @Id entities as EXISTING by default and
 * calls EntityManager.merge() instead of persist().
 *
 * That completely breaks duplicate detection because merge() issues:
 *   SELECT ...
 *   UPDATE ...
 *
 * instead of INSERT.
 *
 * For idempotency semantics we REQUIRE:
 *   INSERT → success      = first processing
 *   INSERT → PK violation = duplicate replay
 *
 * Therefore this entity implements Persistable and always reports isNew=true
 * so Spring Data performs persist() every time.
 */
@Entity
@Table(
        name = "processed_events",
        indexes = {
                @Index(name = "idx_processed_events_id", columnList = "event_id", unique = true),
                @Index(name = "idx_processed_events_expires", columnList = "expires_at")
        }
)
public class ProcessedEvent implements Persistable<String> {

    public static final long DEFAULT_TTL_DAYS = 7;

    @Id
    @Column(name = "event_id", nullable = false, unique = true, length = 255)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Transient
    private boolean isNew = true;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
        this.expiresAt = processedAt.plusSeconds(DEFAULT_TTL_DAYS * 86_400);
    }

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
