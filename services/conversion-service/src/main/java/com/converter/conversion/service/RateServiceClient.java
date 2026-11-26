package com.converter.conversion.service;

import com.converter.common.dto.CurrenciesResponseDto;
import com.converter.common.web.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

@Component
public class RateServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RateServiceClient.class);

    private final RestClient restClient;

    public RateServiceClient(RestClient rateServiceRestClient) {
        this.restClient = rateServiceRestClient;
    }

    public CurrenciesResponseDto fetchRates(LocalDate date, String requestId) {
        String uri = date != null ? "/api/v1/rates?date=" + date : "/api/v1/rates";
        RestClient.RequestHeadersSpec<?> spec = restClient.get().uri(uri);
        if (requestId != null && !requestId.isBlank()) {
            spec = spec.header(RequestIdFilter.HEADER_NAME, requestId);
        }
        CurrenciesResponseDto body = spec.retrieve().body(CurrenciesResponseDto.class);
        if (body == null || body.currencies() == null || body.currencies().isEmpty()) {
            log.warn("rate-service returned empty bulletin for date {}", date);
            throw new IllegalStateException("rate-service returned an empty bulletin");
        }
        return body;
    }
}
