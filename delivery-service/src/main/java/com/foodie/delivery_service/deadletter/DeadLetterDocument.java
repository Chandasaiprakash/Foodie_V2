package com.foodie.delivery_service.deadletter;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

/**
 * Dead-letter record for delivery-service (MongoDB).
 *
 * <p>Uses a compound unique index on {@code (sourceTopic, originalKey)} for
 * replay-safe idempotent inserts — same contract as the JPA version but
 * implemented via MongoDB's unique index.
 */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
@Document(collection = "dead_letters")
@CompoundIndexes({
    @CompoundIndex(name = "idx_dl_topic_key", def = "{'sourceTopic': 1, 'originalKey': 1}", unique = true)
})
public class DeadLetterDocument {

    @Id
    private String id;

    private String sourceTopic;
    private String originalKey;
    private String payloadJson;
    private String aggregateId;
    private String lastExceptionClass;
    private String lastExceptionMessage;

    @Builder.Default
    private Instant failedAt = Instant.now();

    private int retryCount;
    private String correlationId;

    /** PENDING | REPLAYING | REPLAYED | IGNORED */
    @Indexed
    @Builder.Default
    private String replayStatus = "PENDING";

    private Instant replayedAt;
    private String replayNote;
}
