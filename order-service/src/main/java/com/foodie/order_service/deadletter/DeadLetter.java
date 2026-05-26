package com.foodie.order_service.deadletter;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Persistent record of every message that exhausted its Kafka retry budget
 * and landed on a dead-letter topic in order-service.
 *
 * <p>Replay safety contract:
 * <ul>
 *   <li>One row per original message — {@code source_topic + original_key} is
 *       unique so re-delivery from the DLT itself cannot create duplicate rows.</li>
 *   <li>{@code replay_status} drives the state machine:
 *       PENDING → REPLAYING → REPLAYED | IGNORED.</li>
 *   <li>Replaying sets {@code replay_status = REPLAYING} atomically before
 *       re-publishing; if the replay publish fails the status stays REPLAYING
 *       so the operator knows to retry or escalate.</li>
 *   <li>The original payload JSON is stored verbatim so a replay is byte-for-byte
 *       identical to the original message — no data loss from serialisation round-trips.</li>
 * </ul>
 */
@Entity
@Table(
    name = "dead_letters",
    indexes = {
        @Index(name = "idx_dl_source_key",    columnList = "source_topic, original_key", unique = true),
        @Index(name = "idx_dl_replay_status", columnList = "replay_status"),
        @Index(name = "idx_dl_failed_at",     columnList = "failed_at")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** DLT topic name (e.g. "payment-completed-dlt"). */
    @Column(name = "source_topic", nullable = false, length = 255)
    private String sourceTopic;

    /** Original Kafka message key. */
    @Column(name = "original_key", nullable = false, length = 255)
    private String originalKey;

    /** Raw JSON payload — preserved verbatim for replay. */
    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    /** Business-level ID extracted from the payload (e.g. orderUuid). */
    @Column(name = "aggregate_id", length = 255)
    private String aggregateId;

    @Column(name = "last_exception_class", length = 512)
    private String lastExceptionClass;

    @Column(name = "last_exception_message", length = 1024)
    private String lastExceptionMessage;

    @Column(name = "failed_at", nullable = false)
    @Builder.Default
    private Instant failedAt = Instant.now();

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    /**
     * PENDING | REPLAYING | REPLAYED | IGNORED
     */
    @Column(name = "replay_status", nullable = false, length = 20)
    @Builder.Default
    private String replayStatus = "PENDING";

    @Column(name = "replayed_at")
    private Instant replayedAt;

    @Column(name = "replay_note", length = 512)
    private String replayNote;
}
