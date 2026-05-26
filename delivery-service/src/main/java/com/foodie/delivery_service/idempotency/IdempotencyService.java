package com.foodie.delivery_service.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Atomic idempotency guard for delivery-service (MongoDB).
 *
 * IMPLEMENTATION DIFFERENCE FROM JPA SERVICES:
 *   payment-service and order-service use MySQL + JPA + REQUIRES_NEW transactions.
 *   delivery-service uses MongoDB, which has no multi-document ACID transactions
 *   in standalone mode (replica set required for multi-doc transactions).
 *
 *   We leverage MongoDB's atomic single-document insert + unique index instead:
 *   - ProcessedEventDocument has a unique index on eventId.
 *   - MongoRepository.insert() (not save()) throws DuplicateKeyException if the
 *     document already exists. insert() maps to insertOne() at the driver level —
 *     it is atomic and does not perform an upsert.
 *   - This gives exactly-once semantics without needing a replica set.
 *
 * RACE CONDITION SAFETY:
 *   Two concurrent threads calling claim("x") simultaneously:
 *   - MongoDB processes insertOne operations serially at the document level.
 *   - Exactly one succeeds; the other gets DuplicateKeyException.
 *   - No application-level locking needed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Attempts to claim an event for processing.
     *
     * @param eventId unique identifier for the event
     * @return true  → first time seen, caller MUST process
     *         false → duplicate, caller MUST skip
     */
    public boolean claim(String eventId) {
        try {
            // insert() = MongoDB insertOne() — atomic, throws on duplicate key
            processedEventRepository.insert(new ProcessedEventDocument(eventId));
            log.debug("Idempotency claimed: {}", eventId);
            return true;
        } catch (DuplicateKeyException e) {
            log.info("Duplicate event skipped (MongoDB unique index): {}", eventId);
            return false;
        }
    }
}
