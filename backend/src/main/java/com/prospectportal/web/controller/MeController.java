package com.prospectportal.web.controller;

import com.prospectportal.security.AuthContext;
import com.prospectportal.web.dto.AuthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final AuthContext authContext;

    public MeController(AuthContext authContext) {
        this.authContext = authContext;
    }

    @GetMapping
    public AuthResponse me() {
        var user = authContext.currentUser();
        return new AuthResponse(
            null, user.userId(), user.tenantId(), user.fullName(), user.email(), user.role(),
            "PROFESSIONAL", 1880, 2000
        );
    }
}
