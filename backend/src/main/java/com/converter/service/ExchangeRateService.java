package com.converter.service;

import com.converter.config.AppProperties;
import com.converter.entity.ExchangeRateEntity;
import com.converter.exception.BulletinUnavailableException;
import com.converter.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final Duration NEGATIVE_CACHE_TTL = Duration.ofMinutes(15);

    private final BnmClient bnmClient;
    private final BnmXmlParser bnmXmlParser;
    private final ExchangeRateRepository repository;
    private final ExchangeRatePersistenceService persistenceService;
    private final int maxRollbackDays;

    // Single-flight coordination to prevent Cache Stampede (SEC-02)
    private final ConcurrentHashMap<LocalDate, Object> dateLocks = new ConcurrentHashMap<>();

    // Negative cache to prevent repeated querying for confirmed empty dates (SEC-01)
    private final ConcurrentHashMap<LocalDate, Instant> knownEmptyDates = new ConcurrentHashMap<>();

    public record ResolvedBulletin(
            LocalDate rateDate,
            LocalDate requestedDate,
            String source,
            boolean cached,
            boolean offline,
            int rollbackDaysApplied,
            List<ExchangeRateEntity> rates
    ) {}

    @Autowired
    public ExchangeRateService(
            BnmClient bnmClient,
            BnmXmlParser bnmXmlParser,
            ExchangeRateRepository repository,
            ExchangeRatePersistenceService persistenceService,
            AppProperties appProperties
    ) {
        this.bnmClient = bnmClient;
        this.bnmXmlParser = bnmXmlParser;
        this.repository = repository;
        this.persistenceService = persistenceService != null
                ? persistenceService
                : new ExchangeRatePersistenceService(repository);
        this.maxRollbackDays = appProperties.bnm().maxRollbackDays();
    }

    public ExchangeRateService(
            BnmClient bnmClient,
            BnmXmlParser bnmXmlParser,
            ExchangeRateRepository repository,
            AppProperties appProperties
    ) {
        this(bnmClient, bnmXmlParser, repository, new ExchangeRatePersistenceService(repository), appProperties);
    }

    public ResolvedBulletin getRatesForDate(LocalDate requestedDate) {
        LocalDate date = (requestedDate != null) ? requestedDate : LocalDate.now();
        LocalDate currentDate = date;
        int rollbackDays = 0;

        while (rollbackDays <= maxRollbackDays) {
            // 1. Fast lookup in local PostgreSQL cache
            List<ExchangeRateEntity> cached = repository.findByRateDate(currentDate);
            if (!cached.isEmpty()) {
                log.info("Found {} cached rates in PostgreSQL for date {}", cached.size(), currentDate);
                return createResolvedBulletin(currentDate, date, "National Bank of Moldova (PostgreSQL Cache)", true, false, rollbackDays, cached);
            }

            // Check negative cache
            Instant emptyTimestamp = knownEmptyDates.get(currentDate);
            if (emptyTimestamp != null && Duration.between(emptyTimestamp, Instant.now()).compareTo(NEGATIVE_CACHE_TTL) < 0) {
                log.debug("Date {} is marked as known empty in negative cache, skipping BNM request", currentDate);
                currentDate = currentDate.minusDays(1);
                rollbackDays++;
                continue;
            }

            // 2. Coordinated single-flight fetching from live BNM service
            Object lock = dateLocks.computeIfAbsent(currentDate, k -> new Object());
            boolean networkFailed = false;

            synchronized (lock) {
                // Re-check DB cache inside lock in case a parallel thread just saved it
                cached = repository.findByRateDate(currentDate);
                if (!cached.isEmpty()) {
                    return createResolvedBulletin(currentDate, date, "National Bank of Moldova (PostgreSQL Cache)", true, false, rollbackDays, cached);
                }

                try {
                    String xml = bnmClient.fetchBulletinXml(currentDate);
                    BnmXmlParser.ParsedBulletin parsed = bnmXmlParser.parse(xml, currentDate);

                    if (parsed.rates() != null && parsed.rates().size() >= 5) {
                        log.info("Successfully fetched and parsed {} rates from BNM for date {}", parsed.rates().size(), parsed.bulletinDate());
                        persistenceService.saveRatesIdempotently(parsed.rates());
                        return createResolvedBulletin(parsed.bulletinDate(), date, "National Bank of Moldova", false, false, rollbackDays, parsed.rates());
                    } else {
                        log.warn("BNM bulletin for date {} was empty or had insufficient rates ({})", currentDate, parsed.rates() != null ? parsed.rates().size() : 0);
                        knownEmptyDates.put(currentDate, Instant.now());
                    }
                } catch (Exception e) {
                    log.warn("BNM communication failure for date {}: {}. Failing fast to prevent thread pool exhaustion.", currentDate, e.getMessage());
                    networkFailed = true;
                } finally {
                    dateLocks.remove(currentDate, lock);
                }
            }

            if (networkFailed) {
                // If the remote gateway is down or unreachable, fail fast to DB cache rather than holding threads for 120s
                break;
            }

            // Rollback 1 calendar day
            currentDate = currentDate.minusDays(1);
            rollbackDays++;
        }

        log.warn("Rollback ended or network failed. Attempting fallback to latest available date in DB.");
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

    public void saveRatesIdempotently(List<ExchangeRateEntity> rates) {
        persistenceService.saveRatesIdempotently(rates);
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
