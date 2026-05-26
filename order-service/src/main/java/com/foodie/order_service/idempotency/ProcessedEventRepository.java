package com.foodie.order_service.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /**
     * Bulk-delete rows whose TTL has expired.
     * Called by IdempotencyCleanupScheduler on a cron schedule.
     * Uses the expires_at index — O(log n), not a full table scan.
     */
    @Modifying
    @Query("DELETE FROM ProcessedEvent pe WHERE pe.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
