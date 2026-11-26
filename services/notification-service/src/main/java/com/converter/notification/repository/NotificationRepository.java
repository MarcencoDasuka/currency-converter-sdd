package com.converter.notification.repository;

import com.converter.notification.entity.NotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<NotificationEntity> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Modifying
    @Query(value = """
        INSERT INTO notifications (id, event_id, user_id, event_type, title, message, is_read, created_at)
        VALUES (:id, :eventId, :userId, :eventType, :title, :message, FALSE, :createdAt)
        ON CONFLICT (event_id) DO NOTHING
        """, nativeQuery = true)
    int insertIdempotently(
            @Param("id") UUID id,
            @Param("eventId") UUID eventId,
            @Param("userId") UUID userId,
            @Param("eventType") String eventType,
            @Param("title") String title,
            @Param("message") String message,
            @Param("createdAt") Instant createdAt
    );
}
