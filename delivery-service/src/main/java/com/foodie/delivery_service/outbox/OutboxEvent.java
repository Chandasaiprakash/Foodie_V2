package com.foodie.delivery_service.outbox;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Outbox Pattern — delivery-service (MongoDB).
 *
 * <p>delivery-service uses MongoDB, so the outbox document is a MongoDB collection
 * instead of a JPA entity. The transactional guarantee requires a MongoDB replica set
 * (multi-document transactions). In a single-node dev environment, insert + find is
 * used as a close approximation (see {@link OutboxEventService}).
 *
 * <p>The compound index on {@code status + createdAt} makes the poller query fast
 * even with millions of documents.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("outbox_events")
@CompoundIndex(name = "idx_outbox_status_created", def = "{'status': 1, 'createdAt': 1}")
public class OutboxEvent {

    @Id
    private String id;

    /** Business key — used as Kafka message key (e.g. orderUuid). */
    @Indexed
    private String aggregateId;

    /** Fully-qualified event class name. */
    private String eventType;

    /** Kafka topic to publish to. */
    private String topic;

    /** JSON-serialised event payload. */
    private String payload;

    /** PENDING → PUBLISHED | FAILED. */
    @Builder.Default
    private String status = "PENDING";

    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant publishedAt;

    @Builder.Default
    private int retryCount = 0;

    /** Expiry for cleanup scheduler. */
    @Indexed(expireAfterSeconds = 604800) // MongoDB TTL: 7 days for PUBLISHED events
    private Instant expiresAt;
}
