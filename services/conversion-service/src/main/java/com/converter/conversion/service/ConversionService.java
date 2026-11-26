package com.converter.conversion.service;

import com.converter.common.dto.ConversionRequestDto;
import com.converter.common.dto.ConversionResponseDto;
import com.converter.common.dto.CurrenciesResponseDto;
import com.converter.common.dto.CurrencyDto;
import com.converter.common.events.PlatformEvent;
import com.converter.common.events.RabbitTopologyConstants;
import com.converter.common.exception.PlatformExceptions.BadRequestException;
import com.converter.common.exception.PlatformExceptions.ResourceNotFoundException;
import com.converter.common.exception.PlatformExceptions.ServiceUnavailableException;
import com.converter.common.security.AuthContext;
import com.converter.common.security.AuthenticatedUser;
import com.converter.conversion.dto.ConversionDtos.ConversionExecutionResult;
import com.converter.conversion.dto.ConversionDtos.ConversionHistoryItem;
import com.converter.conversion.entity.ConversionEntity;
import com.converter.conversion.entity.LocalRateSnapshotEntity;
import com.converter.conversion.entity.OutboxEventEntity;
import com.converter.conversion.repository.ConversionRepository;
import com.converter.conversion.repository.LocalRateSnapshotRepository;
import com.converter.conversion.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConversionService {

    private static final Logger log = LoggerFactory.getLogger(ConversionService.class);
    private static final MathContext CALC_CONTEXT = MathContext.DECIMAL128;
    private static final int AMOUNT_SCALE = 4;
    private static final int RATE_SCALE = 6;

    private final RateServiceClient rateServiceClient;
    private final ConversionRepository conversionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final LocalRateSnapshotRepository snapshotRepository;
    private final ConversionOutboxRelay outboxRelay;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ConversionService(
            RateServiceClient rateServiceClient,
            ConversionRepository conversionRepository,
            OutboxEventRepository outboxEventRepository,
            LocalRateSnapshotRepository snapshotRepository,
            ConversionOutboxRelay outboxRelay,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.rateServiceClient = rateServiceClient;
        this.conversionRepository = conversionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.snapshotRepository = snapshotRepository;
        this.outboxRelay = outboxRelay;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ConversionExecutionResult convert(
            ConversionRequestDto request,
            AuthenticatedUser authenticatedUser,
            String requestId
    ) {
        if (request == null || request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Amount must be strictly greater than zero");
        }
        LocalDate today = LocalDate.now(clock);
        if (request.date() != null && request.date().isAfter(today)) {
            throw new BadRequestException("Requested date cannot be in the future");
        }

        String sourceCode = request.sourceCurrency().trim().toUpperCase(Locale.ROOT);
        String targetCode = request.targetCurrency().trim().toUpperCase(Locale.ROOT);

        // Fetch remote rates OUTSIDE any database transaction
        CurrenciesResponseDto bulletin = fetchRatesWithGracefulDegradation(request.date(), requestId);
        ConversionResponseDto response = computeConversion(request.amount(), sourceCode, targetCode, bulletin);

        UUID savedId = null;
        if (authenticatedUser != null) {
            savedId = persistConversionAndOutbox(authenticatedUser.userId(), response, requestId);
            try {
                outboxRelay.relayPendingBatch();
            } catch (Exception ex) {
                log.warn("Immediate outbox relay deferred to scheduler: {}", ex.getMessage());
            }
        }
        return new ConversionExecutionResult(response, savedId);
    }

    public List<ConversionHistoryItem> listHistory(UUID userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return conversionRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, safeLimit))
                .stream()
                .map(ConversionService::toHistoryItem)
                .toList();
    }

    public ConversionHistoryItem getConversion(UUID id, AuthenticatedUser caller) {
        ConversionEntity entity = conversionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("conversion-not-found", "Conversion Not Found", "Conversion record not found"));
        AuthContext.requireOwnerOrAdmin(caller, entity.getUserId());
        return toHistoryItem(entity);
    }

    private CurrenciesResponseDto fetchRatesWithGracefulDegradation(LocalDate date, String requestId) {
        try {
            CurrenciesResponseDto remote = rateServiceClient.fetchRates(date, requestId);
            refreshLocalSnapshot(remote);
            return remote;
        } catch (Exception ex) {
            log.warn("rate-service unavailable ({}), activating graceful degradation local snapshot", ex.getMessage());
            List<LocalRateSnapshotEntity> localRates = snapshotRepository.findAll();
            if (localRates.isEmpty()) {
                throw new ServiceUnavailableException(
                        "bulletin-unavailable",
                        "Rates Unavailable",
                        "Exchange rate service is temporarily unavailable and no local snapshot is cached"
                );
            }
            LocalDate snapshotDate = localRates.stream()
                    .map(LocalRateSnapshotEntity::getRateDate)
                    .max(Comparator.naturalOrder())
                    .orElse(LocalDate.now(clock));
            List<CurrencyDto> dtos = localRates.stream()
                    .map(s -> new CurrencyDto(s.getCurrencyCode(), s.getCurrencyName(), s.getNominal(), s.getRate()))
                    .toList();
            return new CurrenciesResponseDto(
                    snapshotDate,
                    date != null ? date : LocalDate.now(clock),
                    "National Bank of Moldova (Degraded Local Cache)",
                    true,
                    true,
                    0,
                    dtos
            );
        }
    }

    private void refreshLocalSnapshot(CurrenciesResponseDto bulletin) {
        if (bulletin.currencies() == null || bulletin.currencies().isEmpty()) {
            return;
        }
        try {
            Instant now = clock.instant();
            transactionTemplate.executeWithoutResult(status -> {
                for (CurrencyDto c : bulletin.currencies()) {
                    snapshotRepository.upsertSnapshot(
                            c.code(), c.name(), c.nominal(), c.rate(), bulletin.rateDate(), now
                    );
                }
            });
        } catch (Exception ex) {
            log.warn("Could not refresh local rate snapshot: {}", ex.getMessage());
        }
    }

    ConversionResponseDto computeConversion(
            BigDecimal amount,
            String sourceCode,
            String targetCode,
            CurrenciesResponseDto bulletin
    ) {
        Map<String, CurrencyDto> rateMap = bulletin.currencies().stream()
                .collect(Collectors.toMap(
                        c -> c.code().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (a, b) -> a
                ));

        CurrencyDto source = rateMap.get(sourceCode);
        if (source == null) {
            throw new ResourceNotFoundException("currency-not-found", "Currency Not Found",
                    "Source currency code not found in bulletin: " + sourceCode);
        }
        CurrencyDto target = rateMap.get(targetCode);
        if (target == null) {
            throw new ResourceNotFoundException("currency-not-found", "Currency Not Found",
                    "Target currency code not found in bulletin: " + targetCode);
        }

        if (sourceCode.equals(targetCode)) {
            return new ConversionResponseDto(
                    amount,
                    sourceCode,
                    targetCode,
                    amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.ONE.setScale(RATE_SCALE, RoundingMode.HALF_UP),
                    bulletin.rateDate(),
                    bulletin.source(),
                    bulletin.cached(),
                    bulletin.offline(),
                    bulletin.rollbackDaysApplied()
            );
        }

        BigDecimal sourceUnitRate = source.rate()
                .divide(BigDecimal.valueOf(source.nominal()), CALC_CONTEXT);
        BigDecimal targetUnitRate = target.rate()
                .divide(BigDecimal.valueOf(target.nominal()), CALC_CONTEXT);

        BigDecimal rawEffectiveRate = sourceUnitRate.divide(targetUnitRate, CALC_CONTEXT);
        BigDecimal rawConverted = amount.multiply(rawEffectiveRate, CALC_CONTEXT);

        return new ConversionResponseDto(
                amount,
                sourceCode,
                targetCode,
                rawConverted.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                rawEffectiveRate.setScale(RATE_SCALE, RoundingMode.HALF_UP),
                bulletin.rateDate(),
                bulletin.source(),
                bulletin.cached(),
                bulletin.offline(),
                bulletin.rollbackDaysApplied()
        );
    }

    private UUID persistConversionAndOutbox(UUID userId, ConversionResponseDto response, String requestId) {
        UUID conversionId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversionId", conversionId.toString());
        payload.put("amount", response.amount().toPlainString());
        payload.put("sourceCurrency", response.sourceCurrency());
        payload.put("targetCurrency", response.targetCurrency());
        payload.put("convertedAmount", response.convertedAmount().toPlainString());
        payload.put("effectiveRate", response.effectiveRate().toPlainString());
        payload.put("rateDate", response.rateDate().toString());
        payload.put("offline", response.offline());

        PlatformEvent event = new PlatformEvent(
                eventId,
                RabbitTopologyConstants.EVENT_CONVERSION_COMPLETED,
                userId,
                requestId != null ? requestId : "",
                payload,
                now
        );
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize ConversionCompleted event", ex);
        }

        transactionTemplate.executeWithoutResult(status -> {
            conversionRepository.save(new ConversionEntity(
                    conversionId,
                    userId,
                    response.amount().setScale(AMOUNT_SCALE, RoundingMode.HALF_UP),
                    response.sourceCurrency(),
                    response.targetCurrency(),
                    response.convertedAmount(),
                    response.effectiveRate(),
                    response.rateDate(),
                    response.source(),
                    response.offline(),
                    now
            ));
            outboxEventRepository.save(new OutboxEventEntity(
                    UUID.randomUUID(),
                    eventId,
                    RabbitTopologyConstants.EVENT_CONVERSION_COMPLETED,
                    RabbitTopologyConstants.ROUTING_CONVERSION_COMPLETED,
                    payloadJson,
                    now
            ));
        });
        return conversionId;
    }

    private static ConversionHistoryItem toHistoryItem(ConversionEntity e) {
        return new ConversionHistoryItem(
                e.getId(),
                e.getUserId(),
                e.getAmount(),
                e.getSourceCurrency(),
                e.getTargetCurrency(),
                e.getConvertedAmount(),
                e.getEffectiveRate(),
                e.getRateDate(),
                e.getSourceLabel(),
                e.isOffline(),
                e.getCreatedAt()
        );
    }
}
