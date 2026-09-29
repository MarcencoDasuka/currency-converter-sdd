package com.converter.dto;

import java.math.BigDecimal;

public record CurrencyDto(
    String code,
    String name,
    int nominal,
    BigDecimal rate
) {}
