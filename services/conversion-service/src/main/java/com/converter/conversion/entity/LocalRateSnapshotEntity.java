package com.converter.conversion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "local_rate_snapshots")
public class LocalRateSnapshotEntity {

    @Id
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "currency_name", nullable = false, length = 128)
    private String currencyName;

    @Column(name = "nominal", nullable = false)
    private int nominal;

    @Column(name = "rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LocalRateSnapshotEntity() {}

    public String getCurrencyCode() { return currencyCode; }
    public String getCurrencyName() { return currencyName; }
    public int getNominal() { return nominal; }
    public BigDecimal getRate() { return rate; }
    public LocalDate getRateDate() { return rateDate; }
    public Instant getUpdatedAt() { return updatedAt; }
}
