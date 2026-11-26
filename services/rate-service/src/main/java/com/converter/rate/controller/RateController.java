package com.converter.rate.controller;

import com.converter.common.dto.CurrenciesResponseDto;
import com.converter.rate.service.ExchangeRateService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
public class RateController {

    private final ExchangeRateService exchangeRateService;

    public RateController(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @GetMapping({"/currencies", "/rates"})
    public ResponseEntity<CurrenciesResponseDto> getCurrencies(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return ResponseEntity.ok(exchangeRateService.getRatesForDate(date));
    }
}
