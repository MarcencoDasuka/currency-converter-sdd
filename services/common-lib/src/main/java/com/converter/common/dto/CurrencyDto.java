package com.converter.common.dto;

import java.math.BigDecimal;

public record CurrencyDto(
        String code,
        String name,
        int nominal,
        BigDecimal rate
) {}
