package com.converter.dto;

import java.time.LocalDate;
import java.util.List;

public record CurrenciesResponseDto(
    LocalDate rateDate,
    LocalDate requestedDate,
    String source,
    boolean cached,
    boolean offline,
    int rollbackDaysApplied,
    List<CurrencyDto> currencies
) {}
