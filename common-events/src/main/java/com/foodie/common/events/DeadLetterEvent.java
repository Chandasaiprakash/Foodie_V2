package com.foodie.common.events;

import lombok.*;

import java.time.Instant;

/**
 * Carries a dead-lettered message's metadata so any service can persist it
 * in its own dead_letters table and expose it for manual inspection / replay.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterEvent {

    /** The DLT topic the message landed on (e.g. "payment-completed-dlt"). */
    private String sourceTopic;

    /** Original Kafka message key. */
    private String originalKey;

    /** JSON payload as a raw string — preserved verbatim for replay. */
    private String payloadJson;

    /** Service-specific business identifier (orderUuid, etc.) when extractable. */
    private String aggregateId;

    /** Exception class name that caused exhaustion. */
    private String lastExceptionClass;

    /** Exception message. */
    private String lastExceptionMessage;

    /** Timestamp when the message landed on the DLT. */
    @Builder.Default
    private Instant failedAt = Instant.now();

    /** Number of delivery attempts before landing here. */
    private int retryCount;

    /** Correlation ID for distributed trace linkage. */
    private String correlationId;

    /**
     * Replay state machine.
     *   PENDING  – waiting for operator action.
     *   REPLAYING – replay request submitted, awaiting outcome.
     *   REPLAYED – successfully reprocessed.
     *   IGNORED  – operator marked as known/acceptable failure.
     */
    @Builder.Default
    private String replayStatus = "PENDING";
}
