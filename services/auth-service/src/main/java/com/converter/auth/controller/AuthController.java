package com.converter.auth.controller;

import com.converter.auth.dto.AuthDtos.AuthTokenResponse;
import com.converter.auth.dto.AuthDtos.LoginRequest;
import com.converter.auth.dto.AuthDtos.RegisterRequest;
import com.converter.auth.dto.AuthDtos.UserProfileResponse;
import com.converter.auth.service.AuthService;
import com.converter.common.security.AuthContext;
import com.converter.common.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthTokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(HttpServletRequest request) {
        AuthenticatedUser user = AuthContext.requireUser(request);
        return ResponseEntity.ok(authService.getProfile(user.userId()));
    }
}
