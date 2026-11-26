package com.converter.alert.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rate_alerts")
public class RateAlertEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source_currency", nullable = false, length = 3)
    private String sourceCurrency;

    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "target_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal targetRate;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "triggered_rate", precision = 18, scale = 6)
    private BigDecimal triggeredRate;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RateAlertEntity() {}

    public RateAlertEntity(
            UUID id,
            UUID userId,
            String sourceCurrency,
            String targetCurrency,
            String direction,
            BigDecimal targetRate,
            Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.sourceCurrency = sourceCurrency;
        this.targetCurrency = targetCurrency;
        this.direction = direction;
        this.targetRate = targetRate;
        this.active = true;
        this.createdAt = createdAt;
    }

    public boolean matches(BigDecimal currentRate) {
        if (currentRate == null) {
            return false;
        }
        return switch (direction) {
            case "ABOVE" -> currentRate.compareTo(targetRate) >= 0;
            case "BELOW" -> currentRate.compareTo(targetRate) <= 0;
            default -> false;
        };
    }

    public void trigger(BigDecimal effectiveRate, Instant when) {
        this.active = false;
        this.triggeredRate = effectiveRate;
        this.triggeredAt = when;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getSourceCurrency() { return sourceCurrency; }
    public String getTargetCurrency() { return targetCurrency; }
    public String getDirection() { return direction; }
    public BigDecimal getTargetRate() { return targetRate; }
    public boolean isActive() { return active; }
    public BigDecimal getTriggeredRate() { return triggeredRate; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public Instant getCreatedAt() { return createdAt; }
}
