package com.foodie.payment_service.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Scheduled cleanup for expired idempotency records in payment-service.
 * See order-service IdempotencyCleanupScheduler for full design documentation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyCleanupScheduler {

    private final ProcessedEventRepository processedEventRepository;

    @Scheduled(cron = "${idempotency.cleanup.cron:0 0 2 * * *}")
    @Transactional
    public void purgeExpiredRecords() {
        Instant cutoff = Instant.now();
        int deleted = processedEventRepository.deleteExpiredBefore(cutoff);
        log.info("Idempotency cleanup: deleted {} expired records (cutoff={})", deleted, cutoff);
    }
}
