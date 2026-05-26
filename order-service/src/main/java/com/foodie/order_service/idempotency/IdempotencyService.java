package com.foodie.order_service.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic idempotency guard with race-condition protection.
 *
 * Problem with naive existsById() + save():
 *   Thread A checks → not present
 *   Thread B checks → not present  (race window)
 *   Thread A inserts → OK
 *   Thread B inserts → DUPLICATE KEY violation (or double-processing if no constraint)
 *
 * Solution: optimistic insert-first.
 *   - Attempt INSERT inside its own REQUIRES_NEW transaction.
 *   - If the INSERT succeeds → first time seeing this event → caller must process.
 *   - If DataIntegrityViolationException is thrown → duplicate constraint hit → skip.
 *
 * The REQUIRES_NEW propagation is critical: it ensures the INSERT commits (or
 * rolls back on duplicate) independently of the outer business transaction. This
 * prevents the scenario where the business logic rolls back but the idempotency
 * row was already visible to concurrent threads.
 *
 * The unique constraint on processed_events.event_id (defined on the @Table
 * annotation of ProcessedEvent) is the actual database-level enforcement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Attempts to claim an event for processing.
     *
     * @param eventId unique identifier for the event (e.g. "payment-completed::uuid")
     * @return true  → first time seen, caller MUST process the event
     *         false → duplicate, caller MUST skip the event
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String eventId) {
        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(eventId));
            log.debug("Idempotency claimed: {}", eventId);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate event skipped (constraint violation): {}", eventId);
            return false;
        }
    }
}
