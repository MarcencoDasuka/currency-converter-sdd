package com.converter.alert.service;

import com.converter.alert.dto.AlertDtos.AlertResponse;
import com.converter.alert.dto.AlertDtos.CreateAlertRequest;
import com.converter.alert.dto.AlertDtos.EvaluateAlertsResponse;
import com.converter.alert.entity.AlertOutboxEntity;
import com.converter.alert.entity.RateAlertEntity;
import com.converter.alert.repository.AlertOutboxRepository;
import com.converter.alert.repository.RateAlertRepository;
import com.converter.common.dto.CurrenciesResponseDto;
import com.converter.common.dto.CurrencyDto;
import com.converter.common.events.PlatformEvent;
import com.converter.common.events.RabbitTopologyConstants;
import com.converter.common.exception.PlatformExceptions.BadRequestException;
import com.converter.common.exception.PlatformExceptions.ResourceNotFoundException;
import com.converter.common.exception.PlatformExceptions.ServiceUnavailableException;
import com.converter.common.security.AuthContext;
import com.converter.common.security.AuthenticatedUser;
import com.converter.common.web.RequestIdFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final MathContext CALC_CONTEXT = MathContext.DECIMAL128;

    private final RateAlertRepository alertRepository;
    private final AlertOutboxRepository outboxRepository;
    private final AlertOutboxRelay outboxRelay;
    private final RestClient rateRestClient;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AlertService(
            RateAlertRepository alertRepository,
            AlertOutboxRepository outboxRepository,
            AlertOutboxRelay outboxRelay,
            RestClient alertRateRestClient,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.alertRepository = alertRepository;
        this.outboxRepository = outboxRepository;
        this.outboxRelay = outboxRelay;
        this.rateRestClient = alertRateRestClient;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public AlertResponse createAlert(AuthenticatedUser user, CreateAlertRequest request) {
        String source = request.sourceCurrency().trim().toUpperCase(Locale.ROOT);
        String target = request.targetCurrency().trim().toUpperCase(Locale.ROOT);
        if (source.equals(target)) {
            throw new BadRequestException("Source and target currencies must be different");
        }
        RateAlertEntity entity = new RateAlertEntity(
                UUID.randomUUID(),
                user.userId(),
                source,
                target,
                request.direction().trim().toUpperCase(Locale.ROOT),
                request.targetRate().setScale(6, RoundingMode.HALF_UP),
                clock.instant()
        );
        return toResponse(alertRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> listAlerts(AuthenticatedUser user) {
        return alertRepository.findByUserIdOrderByCreatedAtDesc(user.userId())
                .stream()
                .map(AlertService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AlertResponse getAlert(UUID id, AuthenticatedUser user) {
        RateAlertEntity alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("alert-not-found", "Alert Not Found", "Rate alert not found"));
        AuthContext.requireOwnerOrAdmin(user, alert.getUserId());
        return toResponse(alert);
    }

    @Transactional
    public void deleteAlert(UUID id, AuthenticatedUser user) {
        RateAlertEntity alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("alert-not-found", "Alert Not Found", "Rate alert not found"));
        AuthContext.requireOwnerOrAdmin(user, alert.getUserId());
        alertRepository.delete(alert);
    }

    public EvaluateAlertsResponse evaluateAlerts(AuthenticatedUser caller, String requestId) {
        // Fetch live rates OUTSIDE database transaction
        CurrenciesResponseDto bulletin = fetchRates(requestId);
        Map<String, CurrencyDto> rateMap = bulletin.currencies().stream()
                .collect(Collectors.toMap(
                        c -> c.code().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (a, b) -> a
                ));

        EvaluateAlertsResponse result = transactionTemplate.execute(status -> {
            List<RateAlertEntity> activeAlerts = caller.isAdmin()
                    ? alertRepository.findAllActiveForUpdate()
                    : alertRepository.findActiveForUpdateByUserId(caller.userId());

            int triggered = 0;
            Instant now = clock.instant();
            for (RateAlertEntity alert : activeAlerts) {
                CurrencyDto src = rateMap.get(alert.getSourceCurrency());
                CurrencyDto tgt = rateMap.get(alert.getTargetCurrency());
                if (src == null || tgt == null) {
                    continue;
                }
                BigDecimal srcUnit = src.rate().divide(BigDecimal.valueOf(src.nominal()), CALC_CONTEXT);
                BigDecimal tgtUnit = tgt.rate().divide(BigDecimal.valueOf(tgt.nominal()), CALC_CONTEXT);
                BigDecimal effectiveRate = srcUnit.divide(tgtUnit, CALC_CONTEXT).setScale(6, RoundingMode.HALF_UP);

                if (alert.matches(effectiveRate)) {
                    alert.trigger(effectiveRate, now);
                    alertRepository.save(alert);

                    UUID eventId = UUID.randomUUID();
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("alertId", alert.getId().toString());
                    payload.put("sourceCurrency", alert.getSourceCurrency());
                    payload.put("targetCurrency", alert.getTargetCurrency());
                    payload.put("direction", alert.getDirection());
                    payload.put("targetRate", alert.getTargetRate().toPlainString());
                    payload.put("effectiveRate", effectiveRate.toPlainString());
                    payload.put("rateDate", bulletin.rateDate().toString());

                    PlatformEvent event = new PlatformEvent(
                            eventId,
                            RabbitTopologyConstants.EVENT_RATE_ALERT_TRIGGERED,
                            alert.getUserId(),
                            requestId != null ? requestId : "",
                            payload,
                            now
                    );
                    outboxRepository.save(new AlertOutboxEntity(
                            UUID.randomUUID(),
                            eventId,
                            RabbitTopologyConstants.EVENT_RATE_ALERT_TRIGGERED,
                            RabbitTopologyConstants.ROUTING_ALERT_TRIGGERED,
                            serializeEvent(event),
                            now
                    ));
                    triggered++;
                }
            }
            return new EvaluateAlertsResponse(activeAlerts.size(), triggered);
        });

        if (result != null && result.triggeredCount() > 0) {
            try {
                outboxRelay.relayPendingBatch();
            } catch (Exception ex) {
                log.warn("Immediate alert outbox relay deferred to scheduler: {}", ex.getMessage());
            }
        }
        return result != null ? result : new EvaluateAlertsResponse(0, 0);
    }

    private CurrenciesResponseDto fetchRates(String requestId) {
        try {
            RestClient.RequestHeadersSpec<?> spec = rateRestClient.get().uri("/api/v1/rates");
            if (requestId != null && !requestId.isBlank()) {
                spec = spec.header(RequestIdFilter.HEADER_NAME, requestId);
            }
            CurrenciesResponseDto body = spec.retrieve().body(CurrenciesResponseDto.class);
            if (body == null || body.currencies() == null || body.currencies().isEmpty()) {
                throw new ServiceUnavailableException("rate-service returned empty currencies");
            }
            return body;
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Could not reach rate-service during alert evaluation: {}", ex.getMessage());
            throw new ServiceUnavailableException("Exchange rate service is temporarily unavailable");
        }
    }

    private String serializeEvent(PlatformEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize RateAlertTriggered event", ex);
        }
    }

    private static AlertResponse toResponse(RateAlertEntity e) {
        return new AlertResponse(
                e.getId(),
                e.getUserId(),
                e.getSourceCurrency(),
                e.getTargetCurrency(),
                e.getDirection(),
                e.getTargetRate(),
                e.isActive(),
                e.getTriggeredRate(),
                e.getTriggeredAt(),
                e.getCreatedAt()
        );
    }
}
