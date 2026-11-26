package com.converter.common.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PlatformEvent(
        UUID eventId,
        String eventType,
        UUID userId,
        String correlationId,
        Map<String, Object> payload,
        Instant occurredAt
) {}
