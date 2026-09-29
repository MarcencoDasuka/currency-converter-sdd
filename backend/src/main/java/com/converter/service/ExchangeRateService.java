package com.converter.service;

import com.converter.config.AppProperties;
import com.converter.entity.ExchangeRateEntity;
import com.converter.exception.BulletinUnavailableException;
import com.converter.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final BnmClient bnmClient;
    private final BnmXmlParser bnmXmlParser;
    private final ExchangeRateRepository repository;
    private final int maxRollbackDays;

    public record ResolvedBulletin(
            LocalDate rateDate,
            LocalDate requestedDate,
            String source,
            boolean cached,
            boolean offline,
            int rollbackDaysApplied,
            List<ExchangeRateEntity> rates
    ) {}

    public ExchangeRateService(
            BnmClient bnmClient,
            BnmXmlParser bnmXmlParser,
            ExchangeRateRepository repository,
            AppProperties appProperties
    ) {
        this.bnmClient = bnmClient;
        this.bnmXmlParser = bnmXmlParser;
        this.repository = repository;
        this.maxRollbackDays = appProperties.bnm().maxRollbackDays();
    }

    public ResolvedBulletin getRatesForDate(LocalDate requestedDate) {
        LocalDate date = (requestedDate != null) ? requestedDate : LocalDate.now();
        LocalDate currentDate = date;
        int rollbackDays = 0;

        while (rollbackDays <= maxRollbackDays) {
            // 1. Check if we already have this exact date in PostgreSQL cache
            List<ExchangeRateEntity> cached = repository.findByRateDate(currentDate);
            if (!cached.isEmpty()) {
                log.info("Found {} cached rates in PostgreSQL for date {}", cached.size(), currentDate);
                return createResolvedBulletin(currentDate, date, "National Bank of Moldova (PostgreSQL Cache)", true, false, rollbackDays, cached);
            }

            // 2. Try fetching from live BNM service
            try {
                String xml = bnmClient.fetchBulletinXml(currentDate);
                BnmXmlParser.ParsedBulletin parsed = bnmXmlParser.parse(xml, currentDate);

                if (parsed.rates() != null && parsed.rates().size() >= 5) {
                    log.info("Successfully fetched and parsed {} rates from BNM for date {}", parsed.rates().size(), parsed.bulletinDate());
                    saveRatesIdempotently(parsed.rates());
                    return createResolvedBulletin(parsed.bulletinDate(), date, "National Bank of Moldova", false, false, rollbackDays, parsed.rates());
                } else {
                    log.warn("BNM bulletin for date {} was empty or had insufficient rates ({})", currentDate, parsed.rates() != null ? parsed.rates().size() : 0);
                }
            } catch (Exception e) {
                log.warn("Could not retrieve bulletin for date {}: {}", currentDate, e.getMessage());
            }

            // Rollback 1 calendar day
            currentDate = currentDate.minusDays(1);
            rollbackDays++;
        }

        log.warn("Exceeded max rollback limit ({} days). Attempting fallback to latest available date in DB.", maxRollbackDays);
        Optional<LocalDate> latestDateOpt = repository.findLatestAvailableRateDate();
        if (latestDateOpt.isPresent()) {
            LocalDate latestDate = latestDateOpt.get();
            List<ExchangeRateEntity> latestRates = repository.findByRateDate(latestDate);
            if (!latestRates.isEmpty()) {
                log.info("Serving {} rates from latest known DB cache date: {}", latestRates.size(), latestDate);
                return createResolvedBulletin(latestDate, date, "National Bank of Moldova (Offline Cache)", true, true, rollbackDays, latestRates);
            }
        }

        throw new BulletinUnavailableException("No valid rate bulletin available from BNM or PostgreSQL within rollback limit (" + maxRollbackDays + " days)");
    }

    @Transactional
    public void saveRatesIdempotently(List<ExchangeRateEntity> rates) {
        for (ExchangeRateEntity entity : rates) {
            try {
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
            } catch (Exception e) {
                log.error("Failed to upsert rate for {}: {}", entity.getCurrencyCode(), e.getMessage());
            }
        }
    }

    private ResolvedBulletin createResolvedBulletin(
            LocalDate rateDate,
            LocalDate requestedDate,
            String source,
            boolean cached,
            boolean offline,
            int rollbackDaysApplied,
            List<ExchangeRateEntity> valutes
    ) {
        List<ExchangeRateEntity> completeList = new ArrayList<>(valutes);

        // Always ensure base currency MDL is present in the currency list
        boolean hasMdl = completeList.stream().anyMatch(r -> "MDL".equalsIgnoreCase(r.getCurrencyCode()));
        if (!hasMdl) {
            ExchangeRateEntity mdl = new ExchangeRateEntity(
                    498,
                    "MDL",
                    "Moldovan Leu",
                    1,
                    BigDecimal.ONE,
                    rateDate,
                    Instant.now(),
                    "MDL"
            );
            completeList.add(mdl);
        }

        completeList.sort(Comparator.comparing(ExchangeRateEntity::getCurrencyCode));

        return new ResolvedBulletin(
                rateDate,
                requestedDate,
                source,
                cached,
                offline,
                rollbackDaysApplied,
                completeList
        );
    }
}
