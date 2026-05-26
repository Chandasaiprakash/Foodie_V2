package com.foodie.order_service.deadletter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetter, Long> {

    List<DeadLetter> findByReplayStatus(String replayStatus);

    Optional<DeadLetter> findBySourceTopicAndOriginalKey(String sourceTopic, String originalKey);

    @Modifying
    @Query("UPDATE DeadLetter dl SET dl.replayStatus = :status, dl.replayNote = :note WHERE dl.id = :id")
    int updateReplayStatus(@Param("id") Long id,
                           @Param("status") String status,
                           @Param("note") String note);
}
