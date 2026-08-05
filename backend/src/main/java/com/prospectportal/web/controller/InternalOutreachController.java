package com.prospectportal.web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Entrada privada para eventos do outreach-bot; protegida por token de serviço. */
@RestController
@RequestMapping("/api/internal/outreach")
public class InternalOutreachController {
    private static final Logger log = LoggerFactory.getLogger(InternalOutreachController.class);
    private final String token;

    public InternalOutreachController(@Value("${app.outreach.bot-service-token:}") String token) {
        this.token = token;
    }

    @PostMapping("/events")
    public Map<String, Boolean> event(@RequestHeader(value = "X-Mira-Service-Token", required = false) String provided,
                                       @RequestBody Map<String, Object> payload) {
        authorize(provided);
        log.info("outreach-bot event {}", payload.get("type"));
        return Map.of("accepted", true);
    }

    @PostMapping("/reports")
    public Map<String, Boolean> report(@RequestHeader(value = "X-Mira-Service-Token", required = false) String provided,
                                        @RequestBody Map<String, Object> payload) {
        authorize(provided);
        log.info("outreach-bot report received");
        return Map.of("accepted", true);
    }

    private void authorize(String provided) {
        if (token.isBlank() || !token.equals(provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token de serviço inválido");
        }
    }
}
