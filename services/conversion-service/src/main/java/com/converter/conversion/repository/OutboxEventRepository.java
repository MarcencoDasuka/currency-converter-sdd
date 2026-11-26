package com.converter.conversion.repository;

import com.converter.conversion.entity.OutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = """
        SELECT * FROM outbox_events
        WHERE published_at IS NULL
          AND (locked_until IS NULL OR locked_until <= :now)
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEventEntity> claimBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
