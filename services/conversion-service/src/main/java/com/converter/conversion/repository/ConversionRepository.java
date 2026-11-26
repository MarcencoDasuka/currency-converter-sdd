package com.converter.conversion.repository;

import com.converter.conversion.entity.ConversionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConversionRepository extends JpaRepository<ConversionEntity, UUID> {
    List<ConversionEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
