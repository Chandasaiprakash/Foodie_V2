package com.foodie.order_service.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Outbox Pattern — order-service.
 *
 * <p>Every Kafka event that must be published is first written to this table
 * <em>inside the same DB transaction</em> that modifies the business entity
 * (e.g. the {@code orders} row). This eliminates the dual-write problem:
 * either both the business change AND the outbox row commit, or neither does.
 *
 * <p>A background {@link OutboxPoller} reads rows with {@code status = PENDING},
 * publishes them to Kafka, and marks them {@code PUBLISHED}. A separate
 * {@link OutboxCleanupScheduler} purges old {@code PUBLISHED} rows.
 *
 * <p>Failure modes handled:
 * <ul>
 *   <li>Kafka down at commit time → outbox row stays PENDING, poller retries.</li>
 *   <li>Service crash after DB commit but before Kafka send → poller picks it up on restart.</li>
 *   <li>Duplicate publish (at-least-once) → consumers are idempotent via {@code processed_events}.</li>
 * </ul>
 */
@Entity
@Table(
    name = "outbox_events",
    indexes = {
        @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
        @Index(name = "idx_outbox_aggregate_id",   columnList = "aggregate_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The domain object UUID this event relates to (e.g. orderUuid). */
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    /** Fully-qualified event class name — used to rebuild the payload type. */
    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    /** Kafka topic to publish to. */
    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    /** JSON-serialised payload of the event. */
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** PENDING → PUBLISHED. FAILED after maxRetries exhausted. */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    /** How many times the poller has attempted to publish this event. */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    /** Absolute deadline after which a PENDING row is considered stuck and alerted. */
    @Column(name = "expires_at")
    private Instant expiresAt;
}
