package com.prospectportal.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.prospectportal.module.whatsapp.WhatsAppReplyAutomationService;
import com.prospectportal.web.dto.FollowUpApprovalResponse;
import com.prospectportal.web.dto.FollowUpReviewItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Entrada pública da Evolution. Configure o mesmo segredo no header X-Webhook-Secret. */
@RestController
@RequestMapping("/api/webhooks/evolution")
public class EvolutionWebhookController {
    private final WhatsAppReplyAutomationService replyAutomationService;
    private final String webhookSecret;

    public EvolutionWebhookController(WhatsAppReplyAutomationService replyAutomationService,
                                       @Value("${app.evolution.webhook-secret:}") String webhookSecret) {
        this.replyAutomationService = replyAutomationService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(value = "X-Webhook-Secret", required = false) String receivedSecret,
                                        @RequestBody JsonNode payload) {
        if (webhookSecret == null || webhookSecret.isBlank()) return ResponseEntity.status(503).build();
        if (!webhookSecret.equals(receivedSecret)) return ResponseEntity.status(401).build();
        if (payload == null || at(payload, "data.key.fromMe").asBoolean(false)) return ResponseEntity.accepted().build();
        String sender = firstText(payload, "data.key.remoteJid", "data.key.participant", "data.sender", "sender", "from");
        if (sender != null && !sender.endsWith("@g.us")) replyAutomationService.registerReply(sender);
        return ResponseEntity.accepted().build();
    }

    private static String firstText(JsonNode payload, String... paths) {
        for (String path : paths) {
            JsonNode value = at(payload, path);
            if (value != null && value.isTextual() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }

    private static JsonNode at(JsonNode node, String path) {
        JsonNode current = node;
        for (String segment : path.split("\\.")) {
            if (current == null) return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
            current = current.get(segment);
        }
        return current == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : current;
    }

    @org.springframework.web.bind.annotation.GetMapping("/approvals/{token}")
    public FollowUpReviewItem reviewApproval(@org.springframework.web.bind.annotation.PathVariable String token) {
        return replyAutomationService.reviewByToken(token);
    }

    @PostMapping("/approvals/{token}")
    public FollowUpApprovalResponse approve(@org.springframework.web.bind.annotation.PathVariable String token) {
        return replyAutomationService.approveByToken(token);
    }
}
