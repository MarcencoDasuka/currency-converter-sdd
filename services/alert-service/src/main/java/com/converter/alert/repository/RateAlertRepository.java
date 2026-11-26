package com.converter.alert.repository;

import com.converter.alert.entity.RateAlertEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RateAlertRepository extends JpaRepository<RateAlertEntity, UUID> {

    List<RateAlertEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM RateAlertEntity a WHERE a.active = true AND a.userId = :userId")
    List<RateAlertEntity> findActiveForUpdateByUserId(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM RateAlertEntity a WHERE a.active = true")
    List<RateAlertEntity> findAllActiveForUpdate();
}
