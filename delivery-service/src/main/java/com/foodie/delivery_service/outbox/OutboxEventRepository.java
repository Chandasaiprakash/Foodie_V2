package com.foodie.delivery_service.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends MongoRepository<OutboxEvent, String> {

    /**
     * Fetch the next batch of PENDING events, ordered oldest-first.
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
