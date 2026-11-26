package com.converter.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";
    public static final String REQUEST_ATTR = "platform.requestId";
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final String serviceName;

    public RequestIdFilter(String serviceName) {
        this.serviceName = (serviceName == null || serviceName.isBlank()) ? "currency-service" : serviceName;
    }

    public static String currentRequestId(HttpServletRequest request) {
        if (request == null) {
            return MDC.get("request_id");
        }
        Object attr = request.getAttribute(REQUEST_ATTR);
        if (attr instanceof String s && !s.isBlank()) {
            return s;
        }
        return MDC.get("request_id");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER_NAME);
        String requestId = (incoming != null && VALID_REQUEST_ID.matcher(incoming.trim()).matches())
                ? incoming.trim()
                : UUID.randomUUID().toString();

        request.setAttribute(REQUEST_ATTR, requestId);
        response.setHeader(HEADER_NAME, requestId);
        MDC.put("request_id", requestId);
        MDC.put("service", serviceName);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("request_id");
            MDC.remove("service");
        }
    }
}
