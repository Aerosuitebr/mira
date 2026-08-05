package com.prospectportal.web.controller;

import com.prospectportal.module.outreach.entity.OutreachMessage;
import com.prospectportal.module.outreach.repository.OutreachMessageRepository;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Entrada privada para eventos do outreach-bot; protegida por token de serviço. */
@RestController
@RequestMapping("/api/internal/outreach")
public class InternalOutreachController {
    private static final Logger log = LoggerFactory.getLogger(InternalOutreachController.class);
    private final String token;
    private final OutreachMessageRepository messageRepository;

    public InternalOutreachController(@Value("${app.outreach.bot-service-token:}") String token,
                                      OutreachMessageRepository messageRepository) {
        this.token = token;
        this.messageRepository = messageRepository;
    }

    @PostMapping("/events")
    public Map<String, Boolean> event(@RequestHeader(value = "X-Mira-Service-Token", required = false) String provided,
                                       @RequestBody Map<String, Object> payload) {
        authorize(provided);
        applyEvent(payload);
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

    private void applyEvent(Map<String, Object> payload) {
        Object rawMessageId = payload.get("messageId");
        Object rawType = payload.get("type");
        if (!(rawMessageId instanceof String messageId) || !(rawType instanceof String type)) return;
        OutreachMessage message;
        try {
            message = messageRepository.findById(UUID.fromString(messageId)).orElse(null);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        if (message == null) return;

        switch (type) {
            case "STEP1_SENT" -> {
                message.setStatus("WAITING_REPLY");
                message.setSentAt(Instant.now());
                message.setProvider("evolution");
                message.setProviderMessageId(stringValue(payload.get("providerMessageId")));
            }
            case "REPLY_RECEIVED" -> {
                message.setStatus("REPLIED");
                message.setRepliedAt(Instant.now());
            }
            case "STEP2_SENT" -> message.setStatus("STEP2_SENT");
            case "SKIPPED" -> {
                message.setStatus("SKIPPED");
                message.setErrorDetail(stringValue(payload.get("reason")));
            }
            case "FAILED" -> {
                message.setStatus("FAILED");
                message.setErrorDetail(stringValue(payload.get("reason")));
            }
            case "THROTTLED" -> message.setStatus("THROTTLED");
            default -> { return; }
        }
        messageRepository.save(message);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
