package com.prospectportal.web.controller;

import com.prospectportal.module.proposal.ProposalService;
import com.prospectportal.web.dto.ApproveProposalRequest;
import com.prospectportal.web.dto.PublicProposalResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/proposals")
public class PublicProposalController {

    private final ProposalService proposalService;

    public PublicProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @GetMapping("/{token}")
    public PublicProposalResponse get(@PathVariable UUID token) {
        return proposalService.getPublic(token);
    }

    @PostMapping("/{token}/approve")
    public Map<String, String> approve(@PathVariable UUID token, @RequestBody ApproveProposalRequest request) {
        proposalService.approvePublic(token, request);
        return Map.of("status", "APPROVED");
    }
}
