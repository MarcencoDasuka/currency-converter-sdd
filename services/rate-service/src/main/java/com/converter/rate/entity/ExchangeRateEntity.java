package com.converter.rate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(
        name = "exchange_rates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_exchange_rate_currency_date", columnNames = {"currency_code", "rate_date"})
        }
)
public class ExchangeRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numeric_code", nullable = false)
    private Integer numericCode;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "currency_name", nullable = false, length = 128)
    private String currencyName;

    @Column(name = "nominal", nullable = false)
    private Integer nominal = 1;

    @Column(name = "rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "external_id", length = 64)
    private String externalId;

    protected ExchangeRateEntity() {}

    public ExchangeRateEntity(
            Integer numericCode,
            String currencyCode,
            String currencyName,
            Integer nominal,
            BigDecimal rate,
            LocalDate rateDate,
            Instant fetchedAt,
            String externalId
    ) {
        this.numericCode = numericCode;
        this.currencyCode = currencyCode;
        this.currencyName = currencyName;
        this.nominal = nominal != null && nominal > 0 ? nominal : 1;
        this.rate = rate;
        this.rateDate = rateDate;
        this.fetchedAt = fetchedAt;
        this.externalId = externalId;
    }

    public Long getId() {
        return id;
    }

    public Integer getNumericCode() {
        return numericCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public Integer getNominal() {
        return nominal;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public String getExternalId() {
        return externalId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExchangeRateEntity that)) return false;
        return Objects.equals(currencyCode, that.currencyCode) && Objects.equals(rateDate, that.rateDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currencyCode, rateDate);
    }
}
