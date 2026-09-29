package com.converter.controller;

import com.converter.dto.ConversionRequestDto;
import com.converter.dto.ConversionResponseDto;
import com.converter.service.CurrencyConversionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/convert")
public class ConversionController {

    private final CurrencyConversionService conversionService;

    public ConversionController(CurrencyConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @PostMapping
    public ResponseEntity<ConversionResponseDto> convert(
            @Valid @RequestBody ConversionRequestDto request
    ) {
        ConversionResponseDto response = conversionService.convert(request);
        return ResponseEntity.ok(response);
    }
}
