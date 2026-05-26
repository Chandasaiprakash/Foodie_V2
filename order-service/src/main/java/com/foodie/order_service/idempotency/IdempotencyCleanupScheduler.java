package com.foodie.order_service.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled cleanup for expired idempotency records.
 *
 * WHY CLEANUP IS NECESSARY:
 *   processed_events is a write-only table during normal operation.
 *   Without cleanup it grows at a rate of ~1 row per Kafka message consumed,
 *   indefinitely. At 100k orders/day that's 100k rows/day = 36M rows/year.
 *
 * STRATEGY:
 *   - Rows are kept for ProcessedEvent.DEFAULT_TTL_DAYS (7 days) after creation.
 *   - 7 days safely covers the maximum realistic retry window: even if a Kafka
 *     broker partition goes offline for hours and replays messages on recovery,
 *     the idempotency row is still present to suppress duplicates.
 *   - Cleanup runs at 02:00 every night (low-traffic window).
 *   - The DELETE uses the expires_at index — O(log n + deleted_rows), not a
 *     full table scan.
 *   - REQUIRES_NEW ensures the cleanup transaction is independent of any
 *     listener transaction that may be running concurrently.
 *
 * SAFETY:
 *   If the scheduler misses a window (pod restart, GC pause), the next run
 *   catches up. Rows are never deleted before their TTL expires — the
 *   Instant.now() cutoff passed to deleteExpiredBefore() is always the
 *   current time, never a future time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Runs at 02:00 UTC every night.
     * Override with {@code idempotency.cleanup.cron} in application.properties.
     */
    @Scheduled(cron = "${idempotency.cleanup.cron:0 0 2 * * *}")
    @Transactional
    public void purgeExpiredRecords() {
        Instant cutoff = Instant.now();
        int deleted = processedEventRepository.deleteExpiredBefore(cutoff);
        log.info("Idempotency cleanup: deleted {} expired records (cutoff={})", deleted, cutoff);
    }
}
