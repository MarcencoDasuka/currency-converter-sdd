package com.converter.repository;

import com.converter.entity.ExchangeRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends JpaRepository<ExchangeRateEntity, Long> {

    List<ExchangeRateEntity> findByRateDate(LocalDate rateDate);

    Optional<ExchangeRateEntity> findByCurrencyCodeAndRateDate(String currencyCode, LocalDate rateDate);

    @Query("SELECT r FROM ExchangeRateEntity r WHERE r.currencyCode = :currencyCode AND r.rateDate <= :targetDate ORDER BY r.rateDate DESC LIMIT 1")
    Optional<ExchangeRateEntity> findLatestBeforeOrEqual(
            @Param("currencyCode") String currencyCode,
            @Param("targetDate") LocalDate targetDate
    );

    @Query("SELECT DISTINCT r.rateDate FROM ExchangeRateEntity r ORDER BY r.rateDate DESC LIMIT 1")
    Optional<LocalDate> findLatestAvailableRateDate();

    @Modifying
    @Query(value = """
        INSERT INTO exchange_rates (
            numeric_code, currency_code, currency_name, nominal, rate, rate_date, fetched_at, external_id
        ) VALUES (
            :numericCode, :currencyCode, :currencyName, :nominal, :rate, :rateDate, :fetchedAt, :externalId
        )
        ON CONFLICT (currency_code, rate_date)
        DO UPDATE SET
            rate = EXCLUDED.rate,
            nominal = EXCLUDED.nominal,
            currency_name = EXCLUDED.currency_name,
            fetched_at = EXCLUDED.fetched_at,
            external_id = EXCLUDED.external_id
        """, nativeQuery = true)
    void upsertRate(
            @Param("numericCode") Integer numericCode,
            @Param("currencyCode") String currencyCode,
            @Param("currencyName") String currencyName,
            @Param("nominal") Integer nominal,
            @Param("rate") BigDecimal rate,
            @Param("rateDate") LocalDate rateDate,
            @Param("fetchedAt") Instant fetchedAt,
            @Param("externalId") String externalId
    );
}
