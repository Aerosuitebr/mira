package com.prospectportal.web.controller;

import com.prospectportal.module.whatsapp.WhatsAppReplyAutomationService;
import com.prospectportal.web.dto.FollowUpApprovalResponse;
import com.prospectportal.web.dto.FollowUpReviewItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints públicos de aprovação da etapa 2 (sem JWT).
 * Separado do webhook Evolution para não herdar regras de segredo/auth.
 */
@RestController
@RequestMapping("/api/public/outreach/approvals")
public class PublicFollowUpApprovalController {

    private final WhatsAppReplyAutomationService replyAutomationService;

    public PublicFollowUpApprovalController(WhatsAppReplyAutomationService replyAutomationService) {
        this.replyAutomationService = replyAutomationService;
    }

    @GetMapping("/{token}")
    public FollowUpReviewItem review(@PathVariable String token) {
        return replyAutomationService.reviewByToken(token);
    }

    @PostMapping("/{token}")
    public FollowUpApprovalResponse approve(@PathVariable String token) {
        return replyAutomationService.approveByToken(token);
    }
}
