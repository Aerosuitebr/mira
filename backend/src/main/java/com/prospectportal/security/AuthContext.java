package com.prospectportal.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthContext {

    public AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new IllegalStateException("Usuário não autenticado");
        }
        return user;
    }

    public UUID tenantId() {
        return currentUser().tenantId();
    }

    public UUID userId() {
        return currentUser().userId();
    }
}
