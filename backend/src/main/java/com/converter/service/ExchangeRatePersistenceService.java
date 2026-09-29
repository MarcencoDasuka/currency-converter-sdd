package com.converter.service;

import com.converter.entity.ExchangeRateEntity;
import com.converter.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ExchangeRatePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRatePersistenceService.class);
    private final ExchangeRateRepository repository;

    public ExchangeRatePersistenceService(ExchangeRateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void saveRatesIdempotently(List<ExchangeRateEntity> rates) {
        if (rates == null || rates.isEmpty()) {
            return;
        }

        for (ExchangeRateEntity entity : rates) {
            repository.upsertRate(
                    entity.getNumericCode(),
                    entity.getCurrencyCode(),
                    entity.getCurrencyName(),
                    entity.getNominal(),
                    entity.getRate(),
                    entity.getRateDate(),
                    entity.getFetchedAt() != null ? entity.getFetchedAt() : Instant.now(),
                    entity.getExternalId()
            );
        }
        log.info("Successfully persisted {} exchange rates in an atomic transaction", rates.size());
    }
}
