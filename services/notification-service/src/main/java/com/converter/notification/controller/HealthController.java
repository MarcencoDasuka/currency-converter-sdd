package com.converter.notification.controller;

import com.converter.common.health.HealthResponseDto;
import com.converter.common.health.ReadinessCheckExecutor;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@RestController
public class HealthController {

    private final DataSource dataSource;
    private final ConnectionFactory rabbitConnectionFactory;
    private final Clock clock;
    private final String serviceName;

    public HealthController(
            DataSource dataSource,
            ConnectionFactory rabbitConnectionFactory,
            Clock clock,
            @Value("${spring.application.name:notification-service}") String serviceName
    ) {
        this.dataSource = dataSource;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
        this.clock = clock;
        this.serviceName = serviceName;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponseDto> health() {
        return ResponseEntity.ok(ReadinessCheckExecutor.healthy(serviceName, clock));
    }

    @GetMapping("/ready")
    public ResponseEntity<HealthResponseDto.ReadinessResponseDto> ready() {
        Map<String, Supplier<Boolean>> checks = new LinkedHashMap<>();
        checks.put("database", this::checkDatabase);
        checks.put("rabbitmq", this::checkRabbitMq);
        HealthResponseDto.ReadinessResponseDto result = ReadinessCheckExecutor.evaluate(
                serviceName, clock, checks, Duration.ofSeconds(2)
        );
        HttpStatus status = "ready".equals(result.status()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(result);
    }

    private boolean checkDatabase() {
        try (java.sql.Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1")) {
            ps.setQueryTimeout(2);
            ps.execute();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean checkRabbitMq() {
        try (Connection conn = rabbitConnectionFactory.createConnection()) {
            return conn.isOpen();
        } catch (Exception ex) {
            return false;
        }
    }
}
