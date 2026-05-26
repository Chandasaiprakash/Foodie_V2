package com.foodie.payment_service.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Fetch the next batch of PENDING events, ordered oldest-first.
     * Pageable limits the batch size (default 100 per poll).
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    /**
     * Delete PUBLISHED events older than the given timestamp (cleanup job).
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = 'PUBLISHED' AND o.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
