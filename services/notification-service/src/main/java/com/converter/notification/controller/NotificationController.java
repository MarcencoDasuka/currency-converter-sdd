package com.converter.notification.controller;

import com.converter.common.security.AuthContext;
import com.converter.common.security.AuthenticatedUser;
import com.converter.notification.dto.NotificationDtos.NotificationResponse;
import com.converter.notification.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        return ResponseEntity.ok(notificationService.listNotifications(user.userId(), unreadOnly, limit));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        return ResponseEntity.ok(notificationService.markRead(id, user));
    }
}
