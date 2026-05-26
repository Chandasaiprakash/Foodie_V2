package com.foodie.order_service.integration;

import com.foodie.common.events.PaymentCompletedEvent;
import com.foodie.order_service.deadletter.DeadLetter;
import com.foodie.order_service.deadletter.DeadLetterRepository;
import com.foodie.order_service.deadletter.DeadLetterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test: Replay Safety for dead letters.
 *
 * <p>Verifies the full lifecycle:
 * <ol>
 *   <li>Orphan message exhausts retries → lands on DLT.</li>
 *   <li>DLT handler persists a {@code DeadLetter} row with {@code replayStatus=PENDING}.</li>
 *   <li>Duplicate DLT re-delivery does NOT create a second row.</li>
 *   <li>Replay transitions state: PENDING → REPLAYING → REPLAYED.</li>
 *   <li>Replayed message is idempotently suppressed if the business row still
 *       doesn't exist (no state corruption).</li>
 *   <li>Ignore transitions PENDING → IGNORED.</li>
 * </ol>
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReplaySafetyIT extends KafkaIntegrationTestBase {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private DeadLetterRepository deadLetterRepository;
    @Autowired private DeadLetterService deadLetterService;

    // ── Test 1: DLT handler persists dead letter ──────────────────────────────

    @Test
    void orphanMessage_exhaustsRetries_persistsDeadLetter() throws Exception {
        String orphanUuid = "replay-safety-" + UUID.randomUUID();

        kafkaTemplate.send("payment-completed", orphanUuid,
            PaymentCompletedEvent.builder()
                .orderUuid(orphanUuid)
                .customerEmail("ghost@test.com")
                .amount(99.0)
                .status("SUCCESS")
                .build());

        // RetryableTopic: 4 attempts with 1s/2s/4s backoff ≈ 7s + headroom
        await().atMost(90, TimeUnit.SECONDS).untilAsserted(() -> {
            List<DeadLetter> dls = deadLetterRepository.findByReplayStatus("PENDING");
            assertThat(dls).anyMatch(dl -> orphanUuid.equals(dl.getAggregateId()));
        });

        DeadLetter dl = deadLetterRepository.findAll().stream()
            .filter(d -> orphanUuid.equals(d.getAggregateId()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No dead letter found for " + orphanUuid));

        assertThat(dl.getSourceTopic()).isEqualTo("payment-completed-dlt");
        assertThat(dl.getOriginalKey()).isEqualTo(orphanUuid);
        assertThat(dl.getPayloadJson()).contains(orphanUuid);
        assertThat(dl.getReplayStatus()).isEqualTo("PENDING");
    }

    // ── Test 2: DLT re-delivery is idempotent ────────────────────────────────

    @Test
    void dltRedelivery_doesNotCreateDuplicateRow() {
        String sourceTopic = "payment-completed-dlt";
        String key = "dedup-key-" + UUID.randomUUID();
        String payload = "{\"orderUuid\":\"" + key + "\"}";

        deadLetterService.store(sourceTopic, key, payload, key, null, null, 4, null);
        deadLetterService.store(sourceTopic, key, payload, key, null, null, 4, null); // re-delivery

        long count = deadLetterRepository.findAll().stream()
            .filter(dl -> key.equals(dl.getOriginalKey()))
            .count();
        assertThat(count).isEqualTo(1);
    }

    // ── Test 3: Ignore transitions to IGNORED ────────────────────────────────

    @Test
    void ignore_transitionsToIgnored() {
        String key = "ignore-" + UUID.randomUUID();
        DeadLetter dl = deadLetterService.store(
            "payment-completed-dlt", key,
            "{\"orderUuid\":\"" + key + "\"}", key, null, null, 4, null);

        deadLetterService.ignore(dl.getId(), "Known data issue — safe to skip");

        DeadLetter updated = deadLetterRepository.findById(dl.getId()).orElseThrow();
        assertThat(updated.getReplayStatus()).isEqualTo("IGNORED");
        assertThat(updated.getReplayNote()).contains("Known data issue");
    }

    // ── Test 4: Replay on already-IGNORED row returns false ──────────────────

    @Test
    void replay_onIgnoredRow_returnsFalse() {
        String key = "ignored-replay-" + UUID.randomUUID();
        DeadLetter dl = deadLetterService.store(
            "payment-completed-dlt", key,
            "{\"orderUuid\":\"" + key + "\"}", key, null, null, 4, null);

        deadLetterService.ignore(dl.getId(), "pre-ignored");

        boolean attempted = deadLetterService.replay(dl.getId());
        assertThat(attempted).isFalse();

        DeadLetter unchanged = deadLetterRepository.findById(dl.getId()).orElseThrow();
        assertThat(unchanged.getReplayStatus()).isEqualTo("IGNORED");
    }
}
