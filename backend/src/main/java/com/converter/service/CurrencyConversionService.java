package com.converter.service;

import com.converter.dto.ConversionRequestDto;
import com.converter.dto.ConversionResponseDto;
import com.converter.entity.ExchangeRateEntity;
import com.converter.exception.CurrencyNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CurrencyConversionService {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int RESULT_SCALE = 4;
    private static final int RATE_SCALE = 6;

    private final ExchangeRateService exchangeRateService;

    public CurrencyConversionService(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    public ConversionResponseDto convert(ConversionRequestDto request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be strictly greater than zero");
        }

        String sourceCode = request.sourceCurrency().trim().toUpperCase();
        String targetCode = request.targetCurrency().trim().toUpperCase();

        ExchangeRateService.ResolvedBulletin bulletin = exchangeRateService.getRatesForDate(request.date());

        Map<String, ExchangeRateEntity> rateMap = bulletin.rates().stream()
                .collect(Collectors.toMap(
                        e -> e.getCurrencyCode().toUpperCase(),
                        e -> e,
                        (e1, e2) -> e1
                ));

        // Fast-path: Same currency conversion
        if (sourceCode.equals(targetCode)) {
            if (!rateMap.containsKey(sourceCode) && !"MDL".equals(sourceCode)) {
                throw new CurrencyNotFoundException("Currency not supported: " + sourceCode);
            }

            BigDecimal sameRate = BigDecimal.ONE.setScale(RATE_SCALE, RoundingMode.HALF_UP);
            BigDecimal scaledAmount = request.amount().setScale(RESULT_SCALE, RoundingMode.HALF_UP);

            return new ConversionResponseDto(
                    request.amount(),
                    sourceCode,
                    targetCode,
                    scaledAmount,
                    sameRate,
                    bulletin.rateDate(),
                    bulletin.source(),
                    bulletin.cached(),
                    bulletin.offline(),
                    bulletin.rollbackDaysApplied()
            );
        }

        ExchangeRateEntity sourceEntity = rateMap.get(sourceCode);
        if (sourceEntity == null) {
            throw new CurrencyNotFoundException("Source currency not supported: " + sourceCode);
        }

        ExchangeRateEntity targetEntity = rateMap.get(targetCode);
        if (targetEntity == null) {
            throw new CurrencyNotFoundException("Target currency not supported: " + targetCode);
        }

        // Calculate unit rates in MDL per 1 unit of foreign currency:
        // MDL_per_1_Source = source.rate / source.nominal
        BigDecimal mdlPerSource = sourceEntity.getRate().divide(
                BigDecimal.valueOf(sourceEntity.getNominal()), MC
        );

        // MDL_per_1_Target = target.rate / target.nominal
        BigDecimal mdlPerTarget = targetEntity.getRate().divide(
                BigDecimal.valueOf(targetEntity.getNominal()), MC
        );

        // Effective cross rate: 1 Source = (mdlPerSource / mdlPerTarget) Target
        BigDecimal effectiveRate = mdlPerSource.divide(mdlPerTarget, MC);

        // Converted amount = amount * effectiveRate
        BigDecimal convertedAmount = request.amount().multiply(effectiveRate, MC);

        // Rounding applied strictly on the response boundary
        BigDecimal finalConverted = convertedAmount.setScale(RESULT_SCALE, RoundingMode.HALF_UP);
        BigDecimal finalRate = effectiveRate.setScale(RATE_SCALE, RoundingMode.HALF_UP);

        return new ConversionResponseDto(
                request.amount(),
                sourceCode,
                targetCode,
                finalConverted,
                finalRate,
                bulletin.rateDate(),
                bulletin.source(),
                bulletin.cached(),
                bulletin.offline(),
                bulletin.rollbackDaysApplied()
        );
    }
}
