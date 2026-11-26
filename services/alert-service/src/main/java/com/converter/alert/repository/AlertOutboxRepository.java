package com.converter.alert.repository;

import com.converter.alert.entity.AlertOutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlertOutboxRepository extends JpaRepository<AlertOutboxEntity, UUID> {

    @Query(value = """
        SELECT * FROM outbox_events
        WHERE published_at IS NULL
          AND (locked_until IS NULL OR locked_until <= :now)
        ORDER BY created_at ASC
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<AlertOutboxEntity> claimBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);
}
