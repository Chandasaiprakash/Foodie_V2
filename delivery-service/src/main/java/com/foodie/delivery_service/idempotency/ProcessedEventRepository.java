package com.foodie.delivery_service.idempotency;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for idempotency documents in MongoDB.
 *
 * We use MongoRepository.insert() (not save()) in IdempotencyService
 * because insert() maps directly to MongoDB's insertOne() and throws
 * DuplicateKeyException on a unique index violation.
 *
 * save() performs an upsert — it would silently overwrite an existing
 * document and break idempotency.
 */
@Repository
public interface ProcessedEventRepository extends MongoRepository<ProcessedEventDocument, String> {}
