package com.converter.alert.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class AlertOutboxEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "routing_key", nullable = false, length = 128)
    private String routingKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    protected AlertOutboxEntity() {}

    public AlertOutboxEntity(
            UUID id,
            UUID eventId,
            String eventType,
            String routingKey,
            String payloadJson,
            Instant createdAt
    ) {
        this.id = id;
        this.eventId = eventId;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
        this.attempts = 0;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getRoutingKey() { return routingKey; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public int getAttempts() { return attempts; }

    public void markPublished(Instant when) {
        this.publishedAt = when;
        this.lockedUntil = null;
    }

    public void markAttemptFailed(Instant nextUnlock) {
        this.attempts += 1;
        this.lockedUntil = nextUnlock;
    }
}
