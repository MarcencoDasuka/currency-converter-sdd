package com.converter.conversion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "conversions")
public class ConversionEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "source_currency", nullable = false, length = 3)
    private String sourceCurrency;

    @Column(name = "target_currency", nullable = false, length = 3)
    private String targetCurrency;

    @Column(name = "converted_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal convertedAmount;

    @Column(name = "effective_rate", nullable = false, precision = 19, scale = 6)
    private BigDecimal effectiveRate;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "source_label", nullable = false, length = 128)
    private String sourceLabel;

    @Column(name = "offline", nullable = false)
    private boolean offline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConversionEntity() {}

    public ConversionEntity(
            UUID id,
            UUID userId,
            BigDecimal amount,
            String sourceCurrency,
            String targetCurrency,
            BigDecimal convertedAmount,
            BigDecimal effectiveRate,
            LocalDate rateDate,
            String sourceLabel,
            boolean offline,
            Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.amount = amount;
        this.sourceCurrency = sourceCurrency;
        this.targetCurrency = targetCurrency;
        this.convertedAmount = convertedAmount;
        this.effectiveRate = effectiveRate;
        this.rateDate = rateDate;
        this.sourceLabel = sourceLabel;
        this.offline = offline;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public String getSourceCurrency() { return sourceCurrency; }
    public String getTargetCurrency() { return targetCurrency; }
    public BigDecimal getConvertedAmount() { return convertedAmount; }
    public BigDecimal getEffectiveRate() { return effectiveRate; }
    public LocalDate getRateDate() { return rateDate; }
    public String getSourceLabel() { return sourceLabel; }
    public boolean isOffline() { return offline; }
    public Instant getCreatedAt() { return createdAt; }
}
