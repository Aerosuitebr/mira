package com.prospectportal.web.controller;

import com.prospectportal.module.auth.AuthService;
import com.prospectportal.web.dto.AuthResponse;
import com.prospectportal.web.dto.LoginRequest;
import com.prospectportal.web.dto.PublicRegisterRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/public/login")
    public AuthResponse publicLogin(@Valid @RequestBody LoginRequest request) {
        return authService.publicLogin(request);
    }

    @PostMapping("/public/register")
    public AuthResponse registerPublic(@Valid @RequestBody PublicRegisterRequest request) {
        return authService.registerPublic(request);
    }
}
