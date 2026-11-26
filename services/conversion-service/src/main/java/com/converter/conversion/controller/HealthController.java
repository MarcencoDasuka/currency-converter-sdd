package com.converter.conversion.controller;

import com.converter.common.health.HealthResponseDto;
import com.converter.common.health.ReadinessCheckExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

@RestController
public class HealthController {

    private final DataSource dataSource;
    private final Clock clock;
    private final String serviceName;

    public HealthController(
            DataSource dataSource,
            Clock clock,
            @Value("${spring.application.name:conversion-service}") String serviceName
    ) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.serviceName = serviceName;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponseDto> health() {
        return ResponseEntity.ok(ReadinessCheckExecutor.healthy(serviceName, clock));
    }

    @GetMapping("/ready")
    public ResponseEntity<HealthResponseDto.ReadinessResponseDto> ready() {
        HealthResponseDto.ReadinessResponseDto result = ReadinessCheckExecutor.evaluate(
                serviceName,
                clock,
                Map.of("database", this::checkDatabase),
                Duration.ofSeconds(2)
        );
        HttpStatus status = "ready".equals(result.status()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(result);
    }

    private boolean checkDatabase() {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
            ps.setQueryTimeout(2);
            ps.execute();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
