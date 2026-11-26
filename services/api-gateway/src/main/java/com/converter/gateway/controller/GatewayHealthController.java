package com.converter.gateway.controller;

import com.converter.common.health.HealthResponseDto;
import com.converter.common.health.ReadinessCheckExecutor;
import com.converter.gateway.config.GatewayProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@RestController
public class GatewayHealthController {

    private final RestClient probeRestClient;
    private final StringRedisTemplate redisTemplate;
    private final GatewayProperties properties;
    private final Clock clock;
    private final String serviceName;

    public GatewayHealthController(
            RestClient probeRestClient,
            StringRedisTemplate redisTemplate,
            GatewayProperties properties,
            Clock clock,
            @Value("${spring.application.name:api-gateway}") String serviceName
    ) {
        this.probeRestClient = probeRestClient;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
        this.serviceName = serviceName;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponseDto> health() {
        return ResponseEntity.ok(ReadinessCheckExecutor.healthy(serviceName, clock));
    }

    @GetMapping("/ready")
    public ResponseEntity<HealthResponseDto.ReadinessResponseDto> ready() {
        GatewayProperties.Upstreams u = properties.upstreams();
        Map<String, Supplier<Boolean>> checks = new LinkedHashMap<>();
        checks.put("redis", this::checkRedis);
        checks.put("auth-service", () -> checkUpstream(u.authServiceUrl()));
        checks.put("rate-service", () -> checkUpstream(u.rateServiceUrl()));
        checks.put("conversion-service", () -> checkUpstream(u.conversionServiceUrl()));
        checks.put("alert-service", () -> checkUpstream(u.alertServiceUrl()));
        checks.put("notification-service", () -> checkUpstream(u.notificationServiceUrl()));

        HealthResponseDto.ReadinessResponseDto result = ReadinessCheckExecutor.evaluate(
                serviceName, clock, checks, Duration.ofSeconds(2)
        );
        HttpStatus status = "ready".equals(result.status()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(result);
    }

    private boolean checkRedis() {
        try {
            String pong = redisTemplate.getConnectionFactory() != null
                    ? redisTemplate.getConnectionFactory().getConnection().ping()
                    : null;
            return "PONG".equalsIgnoreCase(pong);
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean checkUpstream(String baseUrl) {
        try {
            return Boolean.TRUE.equals(probeRestClient.get()
                    .uri(baseUrl + "/health")
                    .exchange((req, res) -> res.getStatusCode().is2xxSuccessful()));
        } catch (Exception ex) {
            return false;
        }
    }
}
