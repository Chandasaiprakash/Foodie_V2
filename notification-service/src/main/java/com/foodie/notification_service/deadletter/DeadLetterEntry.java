package com.foodie.notification_service.deadletter;

import lombok.*;
import java.time.Instant;

/**
 * In-memory dead-letter record for notification-service.
 *
 * <p>Notification-service has no dedicated database, so dead letters are stored
 * in Redis as JSON hashes under the key {@code notif:dl:{id}}.
 * An index set {@code notif:dl:pending} tracks all PENDING IDs for quick listing.
 *
 * <p>Replay is simply re-publishing the original payload to the original topic;
 * the existing idempotency guard (Redis SETNX) prevents duplicate notifications
 * if a message is replayed within the 24-hour TTL window.
 */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DeadLetterEntry {
    private String id;            // UUID
    private String sourceTopic;
    private String originalKey;
    private String payloadJson;
    private String aggregateId;
    private String lastExceptionClass;
    private String lastExceptionMessage;
    private Instant failedAt;
    private int retryCount;
    private String correlationId;
    /** PENDING | REPLAYING | REPLAYED | IGNORED */
    @Builder.Default
    private String replayStatus = "PENDING";
    private Instant replayedAt;
    private String replayNote;
}
