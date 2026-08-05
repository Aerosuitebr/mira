package com.prospectportal.web.controller;

import com.prospectportal.module.outreach.entity.OutreachMessage;
import com.prospectportal.module.outreach.repository.OutreachMessageRepository;
import com.prospectportal.module.whatsapp.WhatsAppReplyAutomationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
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
    private final WhatsAppReplyAutomationService replyAutomationService;

    public InternalOutreachController(@Value("${app.outreach.bot-service-token:}") String token,
                                      OutreachMessageRepository messageRepository,
                                      WhatsAppReplyAutomationService replyAutomationService) {
        this.token = token;
        this.messageRepository = messageRepository;
        this.replyAutomationService = replyAutomationService;
    }

    @PostMapping("/events")
    @Transactional
    public Map<String, Boolean> event(@RequestHeader(value = "X-Mira-Service-Token", required = false) String provided,
                                       @RequestBody Map<String, Object> payload) {
        authorize(provided);
        applyEvent(payload);
        if ("REPLY_RECEIVED".equals(payload.get("type")) && payload.get("phone") instanceof String phone) {
            replyAutomationService.registerReply(phone);
        }
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
                String actualText = stringValue(payload.get("step1Text"));
                if (actualText != null && !actualText.isBlank()) message.setBody(actualText);
            }
            case "REPLY_RECEIVED" -> {
                message.setStatus("REPLIED");
                message.setRepliedAt(Instant.now());
            }
            case "STEP2_SENT" -> {
                if (message.getOutreachStep() == 2) {
                    message.setStatus("SENT");
                    message.setProvider("evolution");
                    message.setProviderMessageId(stringValue(payload.get("providerMessageId")));
                    message.setSentAt(Instant.now());
                } else {
                    message.setStatus("REPLIED");
                if (!messageRepository.existsByReplyToMessageIdAndOutreachStep(message.getId(), (short) 2)) {
                    OutreachMessage secondStep = new OutreachMessage();
                    secondStep.setCampaign(message.getCampaign());
                    secondStep.setLead(message.getLead());
                    secondStep.setChannel("WHATSAPP");
                    secondStep.setRecipient(message.getRecipient());
                    secondStep.setBody(java.util.Objects.requireNonNullElse(stringValue(payload.get("step2Text")), ""));
                    secondStep.setStatus("SENT");
                    secondStep.setProvider("evolution");
                    secondStep.setProviderMessageId(stringValue(payload.get("providerMessageId")));
                    secondStep.setOutreachStep((short) 2);
                    secondStep.setReplyToMessageId(message.getId());
                    secondStep.setProspectJobId(message.getProspectJobId());
                    secondStep.setSentAt(Instant.now());
                    secondStep.setCreatedAt(Instant.now());
                    messageRepository.save(secondStep);
                }
                }
            }
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
