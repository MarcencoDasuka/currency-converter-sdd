package com.converter.conversion.controller;

import com.converter.common.dto.ConversionRequestDto;
import com.converter.common.dto.ConversionResponseDto;
import com.converter.common.security.AuthContext;
import com.converter.common.security.AuthenticatedUser;
import com.converter.common.web.RequestIdFilter;
import com.converter.conversion.dto.ConversionDtos.ConversionExecutionResult;
import com.converter.conversion.dto.ConversionDtos.ConversionHistoryItem;
import com.converter.conversion.service.ConversionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ConversionController {

    private final ConversionService conversionService;

    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @PostMapping("/convert")
    public ResponseEntity<ConversionResponseDto> convert(
            @Valid @RequestBody ConversionRequestDto requestDto,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.optionalUser(request).orElse(null);
        String requestId = RequestIdFilter.currentRequestId(request);
        ConversionExecutionResult result = conversionService.convert(requestDto, user, requestId);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (result.savedConversionId() != null) {
            builder.header("X-Conversion-Id", result.savedConversionId().toString());
        }
        return builder.body(result.response());
    }

    @GetMapping("/conversions")
    public ResponseEntity<List<ConversionHistoryItem>> listHistory(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        return ResponseEntity.ok(conversionService.listHistory(user.userId(), limit));
    }

    @GetMapping("/conversions/{id}")
    public ResponseEntity<ConversionHistoryItem> getConversion(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        return ResponseEntity.ok(conversionService.getConversion(id, user));
    }
}
