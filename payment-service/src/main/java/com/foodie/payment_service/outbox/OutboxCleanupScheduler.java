package com.foodie.payment_service.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Purges PUBLISHED outbox rows older than 7 days to keep the table lean.
 *
 * <p>PUBLISHED rows are safe to delete after the retention window because:
 * <ol>
 *   <li>The Kafka message is already in the broker (durable log).</li>
 *   <li>Consumers have processed and idempotency-flagged the event.</li>
 *   <li>7 days >> any realistic consumer lag or replay window.</li>
 * </ol>
 *
 * <p>Runs daily at 02:00 to avoid conflicting with peak traffic.
 * Override via {@code outbox.cleanup.cron} in application.properties.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanupScheduler {

    private static final long RETENTION_DAYS = 7;

    private final OutboxEventRepository outboxEventRepository;

    @Scheduled(cron = "${outbox.cleanup.cron:0 0 2 * * *}")
    @Transactional
    public void cleanup() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = outboxEventRepository.deletePublishedBefore(cutoff);
        if (deleted > 0) {
            log.info("Outbox cleanup: deleted {} PUBLISHED rows older than {} days", deleted, RETENTION_DAYS);
        }
    }
}
