package com.prospectportal.web.controller;

import com.prospectportal.module.crm.CrmService;
import com.prospectportal.module.outreach.OutreachService;
import com.prospectportal.web.dto.CreateLeadsRequest;
import com.prospectportal.web.dto.KanbanBoardResponse;
import com.prospectportal.web.dto.KanbanCardResponse;
import com.prospectportal.web.dto.LeadResponse;
import com.prospectportal.web.dto.MoveCardRequest;
import com.prospectportal.web.dto.ApproachStatusResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/crm")
public class CrmController {

    private final CrmService crmService;
    private final OutreachService outreachService;

    public CrmController(CrmService crmService, OutreachService outreachService) {
        this.crmService = crmService;
        this.outreachService = outreachService;
    }

    @GetMapping("/board")
    public KanbanBoardResponse board() {
        return crmService.getBoard();
    }

    @PatchMapping("/cards/{cardId}/move")
    public KanbanCardResponse moveCard(@PathVariable UUID cardId, @RequestBody MoveCardRequest request) {
        return crmService.moveCard(cardId, request);
    }

    @GetMapping("/leads")
    public List<LeadResponse> listLeads() {
        return crmService.listLeads();
    }

    @GetMapping("/approach-status")
    public List<ApproachStatusResponse> approachStatus(@RequestParam List<UUID> companyIds) {
        return outreachService.approachStatus(companyIds);
    }

    @PostMapping("/leads")
    public List<LeadResponse> createLeads(@RequestBody CreateLeadsRequest request) {
        return crmService.createLeads(request);
    }
}
