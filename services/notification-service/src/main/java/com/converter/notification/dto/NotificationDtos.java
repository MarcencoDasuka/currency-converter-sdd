package com.converter.notification.dto;

import java.time.Instant;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {}

    public record NotificationResponse(
            UUID id,
            UUID eventId,
            UUID userId,
            String eventType,
            String title,
            String message,
            boolean read,
            Instant createdAt
    ) {}
}
