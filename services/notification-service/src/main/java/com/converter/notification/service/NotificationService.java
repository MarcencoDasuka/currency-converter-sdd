package com.converter.notification.service;

import com.converter.common.events.PlatformEvent;
import com.converter.common.events.RabbitTopologyConstants;
import com.converter.common.exception.PlatformExceptions.ResourceNotFoundException;
import com.converter.common.security.AuthContext;
import com.converter.common.security.AuthenticatedUser;
import com.converter.notification.dto.NotificationDtos.NotificationResponse;
import com.converter.notification.entity.NotificationEntity;
import com.converter.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean recordFromEvent(PlatformEvent event) {
        String title = buildTitle(event);
        String message = buildMessage(event);
        Instant createdAt = event.occurredAt() != null ? event.occurredAt() : clock.instant();

        int inserted = repository.insertIdempotently(
                UUID.randomUUID(),
                event.eventId(),
                event.userId(),
                event.eventType(),
                title,
                message,
                createdAt
        );
        if (inserted == 0) {
            log.info("Duplicate event_id {} ignored idempotently", event.eventId());
            return false;
        }
        log.info("Recorded notification for event {} ({})", event.eventId(), event.eventType());
        return true;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listNotifications(UUID userId, boolean unreadOnly, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        PageRequest page = PageRequest.of(0, safeLimit);
        List<NotificationEntity> entities = unreadOnly
                ? repository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, page)
                : repository.findByUserIdOrderByCreatedAtDesc(userId, page);
        return entities.stream().map(NotificationService::toResponse).toList();
    }

    @Transactional
    public NotificationResponse markRead(UUID id, AuthenticatedUser caller) {
        NotificationEntity entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("notification-not-found", "Notification Not Found", "Notification not found"));
        AuthContext.requireOwnerOrAdmin(caller, entity.getUserId());
        entity.markRead();
        return toResponse(repository.save(entity));
    }

    private static String buildTitle(PlatformEvent event) {
        if (RabbitTopologyConstants.EVENT_CONVERSION_COMPLETED.equals(event.eventType())) {
            return "Currency conversion recorded";
        }
        if (RabbitTopologyConstants.EVENT_RATE_ALERT_TRIGGERED.equals(event.eventType())) {
            return "Target exchange rate alert triggered";
        }
        return "Platform event: " + event.eventType();
    }

    private static String buildMessage(PlatformEvent event) {
        Map<String, Object> p = event.payload() != null ? event.payload() : Map.of();
        if (RabbitTopologyConstants.EVENT_CONVERSION_COMPLETED.equals(event.eventType())) {
            return String.format(
                    "Converted %s %s to %s %s (rate %s on %s)",
                    p.getOrDefault("amount", "?"),
                    p.getOrDefault("sourceCurrency", "?"),
                    p.getOrDefault("convertedAmount", "?"),
                    p.getOrDefault("targetCurrency", "?"),
                    p.getOrDefault("effectiveRate", "?"),
                    p.getOrDefault("rateDate", "?")
            );
        }
        if (RabbitTopologyConstants.EVENT_RATE_ALERT_TRIGGERED.equals(event.eventType())) {
            return String.format(
                    "Alert %s/%s (%s %s) triggered at effective rate %s",
                    p.getOrDefault("sourceCurrency", "?"),
                    p.getOrDefault("targetCurrency", "?"),
                    p.getOrDefault("direction", "?"),
                    p.getOrDefault("targetRate", "?"),
                    p.getOrDefault("effectiveRate", "?")
            );
        }
        return "Event received: " + event.eventType();
    }

    private static NotificationResponse toResponse(NotificationEntity e) {
        return new NotificationResponse(
                e.getId(),
                e.getEventId(),
                e.getUserId(),
                e.getEventType(),
                e.getTitle(),
                e.getMessage(),
                e.isRead(),
                e.getCreatedAt()
        );
    }
}
