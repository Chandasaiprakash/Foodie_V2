package com.foodie.payment_service.deadletter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetter, Long> {
    List<DeadLetter> findByReplayStatus(String replayStatus);
    Optional<DeadLetter> findBySourceTopicAndOriginalKey(String sourceTopic, String originalKey);
}
