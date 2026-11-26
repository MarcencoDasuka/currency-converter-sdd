package com.converter.conversion.repository;

import com.converter.conversion.entity.LocalRateSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public interface LocalRateSnapshotRepository extends JpaRepository<LocalRateSnapshotEntity, String> {

    @Modifying
    @Query(value = """
        INSERT INTO local_rate_snapshots (currency_code, currency_name, nominal, rate, rate_date, updated_at)
        VALUES (:code, :name, :nominal, :rate, :rateDate, :updatedAt)
        ON CONFLICT (currency_code)
        DO UPDATE SET
            currency_name = EXCLUDED.currency_name,
            nominal = EXCLUDED.nominal,
            rate = EXCLUDED.rate,
            rate_date = EXCLUDED.rate_date,
            updated_at = EXCLUDED.updated_at
        """, nativeQuery = true)
    void upsertSnapshot(
            @Param("code") String code,
            @Param("name") String name,
            @Param("nominal") int nominal,
            @Param("rate") BigDecimal rate,
            @Param("rateDate") LocalDate rateDate,
            @Param("updatedAt") Instant updatedAt
    );
}
