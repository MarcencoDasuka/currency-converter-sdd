package com.converter.rate.service;

import com.converter.common.dto.CurrenciesResponseDto;
import com.converter.common.dto.CurrencyDto;
import com.converter.common.exception.PlatformExceptions.BadRequestException;
import com.converter.common.exception.PlatformExceptions.ServiceUnavailableException;
import com.converter.rate.config.RateProperties;
import com.converter.rate.entity.ExchangeRateEntity;
import com.converter.rate.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
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
    private final Clock clock;
    private final int maxRollbackDays;

    private final ConcurrentHashMap<LocalDate, Object> dateLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<LocalDate, Instant> knownEmptyDates = new ConcurrentHashMap<>();

    public ExchangeRateService(
            BnmClient bnmClient,
            BnmXmlParser bnmXmlParser,
            ExchangeRateRepository repository,
            ExchangeRatePersistenceService persistenceService,
            RateProperties properties,
            Clock clock
    ) {
        this.bnmClient = bnmClient;
        this.bnmXmlParser = bnmXmlParser;
        this.repository = repository;
        this.persistenceService = persistenceService;
        this.maxRollbackDays = properties.maxRollbackDays();
        this.clock = clock;
    }

    public CurrenciesResponseDto getRatesForDate(LocalDate requestedDate) {
        LocalDate today = LocalDate.now(clock);
        LocalDate date = requestedDate != null ? requestedDate : today;
        if (date.isAfter(today)) {
            throw new BadRequestException("Requested date cannot be in the future");
        }

        LocalDate currentDate = date;
        int rollbackDays = 0;

        while (rollbackDays <= maxRollbackDays) {
            List<ExchangeRateEntity> cached = repository.findByRateDate(currentDate);
            if (!cached.isEmpty()) {
                log.info("Serving {} cached rates from PostgreSQL for date {}", cached.size(), currentDate);
                return toResponse(currentDate, date, "National Bank of Moldova (PostgreSQL Cache)", true, false, rollbackDays, cached);
            }

            Instant emptyTimestamp = knownEmptyDates.get(currentDate);
            if (emptyTimestamp != null && Duration.between(emptyTimestamp, clock.instant()).compareTo(NEGATIVE_CACHE_TTL) < 0) {
                currentDate = currentDate.minusDays(1);
                rollbackDays++;
                continue;
            }

            Object lock = dateLocks.computeIfAbsent(currentDate, k -> new Object());
            boolean networkFailed = false;
            synchronized (lock) {
                cached = repository.findByRateDate(currentDate);
                if (!cached.isEmpty()) {
                    return toResponse(currentDate, date, "National Bank of Moldova (PostgreSQL Cache)", true, false, rollbackDays, cached);
                }
                try {
                    String xml = bnmClient.fetchBulletinXml(currentDate);
                    BnmXmlParser.ParsedBulletin parsed = bnmXmlParser.parse(xml, currentDate);
                    if (parsed.rates() != null && parsed.rates().size() >= 5) {
                        persistenceService.saveRatesIdempotently(parsed.rates());
                        return toResponse(parsed.bulletinDate(), date, "National Bank of Moldova", false, false, rollbackDays, parsed.rates());
                    } else {
                        log.warn("BNM bulletin for {} had insufficient rates", currentDate);
                        knownEmptyDates.put(currentDate, clock.instant());
                    }
                } catch (Exception ex) {
                    log.warn("BNM fetch failed for {}: {}", currentDate, ex.getMessage());
                    networkFailed = true;
                } finally {
                    dateLocks.remove(currentDate, lock);
                }
            }

            if (networkFailed) {
                break;
            }
            currentDate = currentDate.minusDays(1);
            rollbackDays++;
        }

        Optional<LocalDate> latestDateOpt = repository.findLatestAvailableRateDate();
        if (latestDateOpt.isPresent()) {
            LocalDate latestDate = latestDateOpt.get();
            List<ExchangeRateEntity> latestRates = repository.findByRateDate(latestDate);
            if (!latestRates.isEmpty()) {
                log.info("Serving {} rates from offline PostgreSQL fallback date {}", latestRates.size(), latestDate);
                return toResponse(latestDate, date, "National Bank of Moldova (Offline Cache)", true, true, rollbackDays, latestRates);
            }
        }

        throw new ServiceUnavailableException(
                "bulletin-unavailable",
                "Bulletin Unavailable",
                "No valid rate bulletin available from BNM or PostgreSQL within rollback limit (" + maxRollbackDays + " days)"
        );
    }

    public void clearNegativeCache() {
        knownEmptyDates.clear();
    }

    private CurrenciesResponseDto toResponse(
            LocalDate rateDate,
            LocalDate requestedDate,
            String source,
            boolean cached,
            boolean offline,
            int rollbackDaysApplied,
            List<ExchangeRateEntity> valutes
    ) {
        List<ExchangeRateEntity> completeList = new ArrayList<>(valutes);
        boolean hasMdl = completeList.stream().anyMatch(r -> "MDL".equalsIgnoreCase(r.getCurrencyCode()));
        if (!hasMdl) {
            completeList.add(new ExchangeRateEntity(
                    498, "MDL", "Moldovan Leu", 1, BigDecimal.ONE, rateDate, clock.instant(), "MDL"
            ));
        }
        completeList.sort(Comparator.comparing(ExchangeRateEntity::getCurrencyCode));
        List<CurrencyDto> currencies = completeList.stream()
                .map(r -> new CurrencyDto(r.getCurrencyCode(), r.getCurrencyName(), r.getNominal(), r.getRate()))
                .toList();
        return new CurrenciesResponseDto(rateDate, requestedDate, source, cached, offline, rollbackDaysApplied, currencies);
    }
}
