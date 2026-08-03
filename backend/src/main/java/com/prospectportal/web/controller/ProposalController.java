package com.prospectportal.web.controller;

import com.prospectportal.module.proposal.ProposalService;
import com.prospectportal.web.dto.CreateProposalRequest;
import com.prospectportal.web.dto.ProposalRecipientOption;
import com.prospectportal.web.dto.ProposalResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/proposals")
public class ProposalController {

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    @GetMapping("/recipients")
    public List<ProposalRecipientOption> recipients(@RequestParam(required = false) String search) {
        return proposalService.listRecipients(search);
    }

    @GetMapping
    public List<ProposalResponse> list() {
        return proposalService.list();
    }

    @GetMapping("/{id}")
    public ProposalResponse get(@PathVariable UUID id) {
        return proposalService.get(id);
    }

    @PostMapping
    public ProposalResponse create(@RequestBody CreateProposalRequest request) {
        return proposalService.create(request);
    }

    @PostMapping("/{id}/publish")
    public ProposalResponse publish(@PathVariable UUID id) {
        return proposalService.publish(id);
    }
}
