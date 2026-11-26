package com.converter.alert.controller;

import com.converter.alert.dto.AlertDtos.AlertResponse;
import com.converter.alert.dto.AlertDtos.CreateAlertRequest;
import com.converter.alert.dto.AlertDtos.EvaluateAlertsResponse;
import com.converter.alert.service.AlertService;
import com.converter.common.security.AuthContext;
import com.converter.common.security.AuthenticatedUser;
import com.converter.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    public ResponseEntity<AlertResponse> create(
            @Valid @RequestBody CreateAlertRequest requestDto,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.createAlert(user, requestDto));
    }

    @GetMapping
    public ResponseEntity<List<AlertResponse>> list(HttpServletRequest request) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        return ResponseEntity.ok(alertService.listAlerts(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> get(@PathVariable UUID id, HttpServletRequest request) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        return ResponseEntity.ok(alertService.getAlert(id, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        alertService.deleteAlert(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/evaluate")
    public ResponseEntity<EvaluateAlertsResponse> evaluate(HttpServletRequest request) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        String requestId = RequestIdFilter.currentRequestId(request);
        return ResponseEntity.ok(alertService.evaluateAlerts(user, requestId));
    }
}
