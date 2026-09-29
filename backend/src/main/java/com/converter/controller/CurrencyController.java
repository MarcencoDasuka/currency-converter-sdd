package com.converter.controller;

import com.converter.dto.CurrenciesResponseDto;
import com.converter.dto.CurrencyDto;
import com.converter.service.ExchangeRateService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/currencies")
public class CurrencyController {

    private final ExchangeRateService exchangeRateService;

    public CurrencyController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping
    public ResponseEntity<CurrenciesResponseDto> getCurrencies(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        if (date != null) {
            if (date.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Date cannot be in the future");
            }
            if (date.isBefore(LocalDate.of(1994, 1, 1))) {
                throw new IllegalArgumentException("Date cannot be earlier than 1994-01-01");
            }
        }

        ExchangeRateService.ResolvedBulletin bulletin = exchangeRateService.getRatesForDate(date);

        List<CurrencyDto> currencyDtos = bulletin.rates().stream()
                .map(r -> new CurrencyDto(
                        r.getCurrencyCode(),
                        r.getCurrencyName(),
                        r.getNominal(),
                        r.getRate()
                ))
                .toList();

        CurrenciesResponseDto response = new CurrenciesResponseDto(
                bulletin.rateDate(),
                bulletin.requestedDate(),
                bulletin.source(),
                bulletin.cached(),
                bulletin.offline(),
                bulletin.rollbackDaysApplied(),
                currencyDtos
        );

        return ResponseEntity.ok(response);
    }
}
