package com.foodie.payment_service.deadletter;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** Dead-letter record for payment-service. See order-service DeadLetter for full design notes. */
@Entity
@Table(
    name = "dead_letters",
    indexes = {
        @Index(name = "idx_dl_source_key",    columnList = "source_topic, original_key", unique = true),
        @Index(name = "idx_dl_replay_status", columnList = "replay_status"),
        @Index(name = "idx_dl_failed_at",     columnList = "failed_at")
    }
)
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DeadLetter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_topic", nullable = false, length = 255)
    private String sourceTopic;

    @Column(name = "original_key", nullable = false, length = 255)
    private String originalKey;

    @Lob @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "aggregate_id", length = 255)
    private String aggregateId;

    @Column(name = "last_exception_class", length = 512)
    private String lastExceptionClass;

    @Column(name = "last_exception_message", length = 1024)
    private String lastExceptionMessage;

    @Column(name = "failed_at", nullable = false) @Builder.Default
    private Instant failedAt = Instant.now();

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "replay_status", nullable = false, length = 20) @Builder.Default
    private String replayStatus = "PENDING";

    @Column(name = "replayed_at")
    private Instant replayedAt;

    @Column(name = "replay_note", length = 512)
    private String replayNote;
}
