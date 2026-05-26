package com.foodie.delivery_service.idempotency;

import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Idempotency guard document for delivery-service (MongoDB).
 *
 * UNIQUE INDEX:
 *   @Indexed(unique = true) on eventId creates a MongoDB unique index.
 *   Combined with IdempotencyService.insert() (not save/upsert), this
 *   gives atomic exactly-once semantics per eventId.
 *
 * TTL INDEX:
 *   @Indexed(expireAfterSeconds = 604800) on expiresAt creates a MongoDB
 *   TTL index. MongoDB's background thread automatically deletes documents
 *   once their expiresAt field passes — no application-level cron needed.
 *   604800 seconds = 7 days.
 *
 *   MongoDB TTL is eventually consistent (runs ~every 60 seconds), which
 *   is perfectly fine — a record expiring 60 seconds late poses no risk.
 */
@Getter
@Document(collection = "processed_events")
public class ProcessedEventDocument {

    /** Default retention: 7 days. */
    public static final long DEFAULT_TTL_SECONDS = 7 * 24 * 3600; // 604800

    @Id
    private String id;

    @Indexed(unique = true)
    private String eventId;

    private Instant processedAt;

    /**
     * TTL index field. MongoDB deletes the document when the wall clock
     * reaches this value. The expireAfterSeconds=0 means "expire at the
     * exact time stored in the field" — the field itself is the deadline.
     */
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    protected ProcessedEventDocument() {}

    public ProcessedEventDocument(String eventId) {
        this.eventId     = eventId;
        this.processedAt = Instant.now();
        this.expiresAt   = processedAt.plusSeconds(DEFAULT_TTL_SECONDS);
    }
}
