package com.converter.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.LayoutBase;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Single-line structured JSON log layout with secret redaction and MDC correlation fields.
 */
public class JsonConsoleLayout extends LayoutBase<ILoggingEvent> {

    private static final Pattern BEARER_PATTERN =
            Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/=-]+");
    private static final Pattern SECRET_KV_PATTERN =
            Pattern.compile("(?i)(password|secret|token|jwt_secret|api_key)\\s*([=:])\\s*[^\\s,;\"'}]+");

    private String service = "currency-service";

    public void setService(String service) {
        if (service != null && !service.isBlank()) {
            this.service = service;
        }
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        Map<String, String> mdc = event.getMDCPropertyMap();
        String svc = mdc.getOrDefault("service", service);
        String requestId = mdc.getOrDefault("request_id", "");
        String userId = mdc.getOrDefault("user_id", "");
        String msg = redact(event.getFormattedMessage());

        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        appendField(sb, "timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        sb.append(',');
        appendField(sb, "level", event.getLevel().toString());
        sb.append(',');
        appendField(sb, "service", svc);
        sb.append(',');
        appendField(sb, "request_id", requestId);
        if (!userId.isBlank()) {
            sb.append(',');
            appendField(sb, "user_id", userId);
        }
        sb.append(',');
        appendField(sb, "logger", event.getLoggerName());
        sb.append(',');
        appendField(sb, "message", msg);

        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            sb.append(',');
            appendField(sb, "error_type", throwable.getClassName());
            if (throwable.getMessage() != null) {
                sb.append(',');
                appendField(sb, "error_message", redact(throwable.getMessage()));
            }
        }
        sb.append('}').append(System.lineSeparator());
        return sb.toString();
    }

    static String redact(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String sanitized = BEARER_PATTERN.matcher(raw).replaceAll("$1[REDACTED]");
        return SECRET_KV_PATTERN.matcher(sanitized).replaceAll("$1$2[REDACTED]");
    }

    private static void appendField(StringBuilder sb, String key, String value) {
        sb.append('"').append(escapeJson(key)).append("\":\"").append(escapeJson(value)).append('"');
    }

    private static String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
