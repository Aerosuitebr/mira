package com.prospectportal.web.controller;

import com.prospectportal.module.crm.CrmService;
import com.prospectportal.web.dto.CreateLeadsRequest;
import com.prospectportal.web.dto.KanbanBoardResponse;
import com.prospectportal.web.dto.KanbanCardResponse;
import com.prospectportal.web.dto.LeadResponse;
import com.prospectportal.web.dto.MoveCardRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/crm")
public class CrmController {

    private final CrmService crmService;

    public CrmController(CrmService crmService) {
        this.crmService = crmService;
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

    @PostMapping("/leads")
    public List<LeadResponse> createLeads(@RequestBody CreateLeadsRequest request) {
        return crmService.createLeads(request);
    }
}
