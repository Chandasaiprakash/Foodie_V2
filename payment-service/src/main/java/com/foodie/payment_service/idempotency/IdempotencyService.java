package com.foodie.payment_service.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Atomic idempotency guard — insert-first with unique constraint enforcement.
 * See order-service IdempotencyService for full explanation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String eventId) {
        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(eventId));
            log.debug("Idempotency claimed: {}", eventId);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate event skipped (constraint): {}", eventId);
            return false;
        }
    }
}
