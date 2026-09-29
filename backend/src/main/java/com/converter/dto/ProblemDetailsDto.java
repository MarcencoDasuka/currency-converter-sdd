package com.converter.dto;

import java.net.URI;
import java.util.List;
import java.util.Map;

public record ProblemDetailsDto(
    URI type,
    String title,
    int status,
    String detail,
    String instance,
    Map<String, Object> properties,
    List<InvalidParam> invalidParams
) {
    public record InvalidParam(
        String name,
        String reason
    ) {}
}
