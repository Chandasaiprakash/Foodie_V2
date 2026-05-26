package com.foodie.delivery_service.deadletter;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeadLetterRepository extends MongoRepository<DeadLetterDocument, String> {
    List<DeadLetterDocument> findByReplayStatus(String replayStatus);
    Optional<DeadLetterDocument> findBySourceTopicAndOriginalKey(String sourceTopic, String originalKey);
}
