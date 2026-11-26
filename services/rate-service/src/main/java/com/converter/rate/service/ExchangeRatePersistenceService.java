package com.converter.rate.service;

import com.converter.rate.entity.ExchangeRateEntity;
import com.converter.rate.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ExchangeRatePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRatePersistenceService.class);

    private final ExchangeRateRepository repository;
    private final Clock clock;

    public ExchangeRatePersistenceService(ExchangeRateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void saveRatesIdempotently(List<ExchangeRateEntity> rates) {
        if (rates == null || rates.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        for (ExchangeRateEntity entity : rates) {
            repository.upsertRate(
                    entity.getNumericCode(),
                    entity.getCurrencyCode(),
                    entity.getCurrencyName(),
                    entity.getNominal(),
                    entity.getRate(),
                    entity.getRateDate(),
                    entity.getFetchedAt() != null ? entity.getFetchedAt() : now,
                    entity.getExternalId()
            );
        }
        log.info("Persisted {} exchange rates atomically via PostgreSQL upsert", rates.size());
    }
}
