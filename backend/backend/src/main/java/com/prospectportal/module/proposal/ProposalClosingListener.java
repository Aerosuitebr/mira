package com.prospectportal.module.proposal;

import com.prospectportal.module.client.ClientConversionService;
import com.prospectportal.module.client.entity.Client;
import com.prospectportal.module.crm.entity.CrmCard;
import com.prospectportal.module.crm.entity.CrmPipeline;
import com.prospectportal.module.crm.entity.CrmStage;
import com.prospectportal.module.crm.entity.Lead;
import com.prospectportal.module.crm.repository.CrmCardRepository;
import com.prospectportal.module.crm.repository.CrmPipelineRepository;
import com.prospectportal.module.crm.repository.CrmStageRepository;
import com.prospectportal.module.crm.repository.LeadRepository;
import com.prospectportal.module.project.ProjectService;
import com.prospectportal.module.proposal.entity.Proposal;
import com.prospectportal.module.proposal.event.ProposalApprovedEvent;
import com.prospectportal.module.proposal.repository.ProposalRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class ProposalClosingListener {

    private final ProposalRepository proposalRepository;
    private final LeadRepository leadRepository;
    private final ClientConversionService clientConversionService;
    private final CrmCardRepository cardRepository;
    private final CrmPipelineRepository pipelineRepository;
    private final CrmStageRepository stageRepository;
    private final ProjectService projectService;

    public ProposalClosingListener(
        ProposalRepository proposalRepository,
        LeadRepository leadRepository,
        ClientConversionService clientConversionService,
        CrmCardRepository cardRepository,
        CrmPipelineRepository pipelineRepository,
        CrmStageRepository stageRepository,
        ProjectService projectService
    ) {
        this.proposalRepository = proposalRepository;
        this.leadRepository = leadRepository;
        this.clientConversionService = clientConversionService;
        this.cardRepository = cardRepository;
        this.pipelineRepository = pipelineRepository;
        this.stageRepository = stageRepository;
        this.projectService = projectService;
    }

    @EventListener
    @Transactional
    public void onProposalApproved(ProposalApprovedEvent event) {
        Proposal proposal = proposalRepository.findById(event.proposalId()).orElse(null);
        if (proposal == null) {
            return;
        }

        Client client;
        Lead lead = proposal.getLead();
        if (lead != null) {
            client = clientConversionService.ensureFromWonDeal(lead, event.totalAmount(), lead.getOwner());
            lead.setStatus("WON");
            lead.setUpdatedAt(Instant.now());
            leadRepository.save(lead);
            moveCardToWon(lead, event.tenantId(), event.totalAmount());
        } else if (proposal.getClient() != null) {
            client = proposal.getClient();
        } else {
            return;
        }

        proposal.setClient(client);
        proposalRepository.save(proposal);
        projectService.bootstrapFromProposal(proposal, client);
    }

    private void moveCardToWon(Lead lead, UUID tenantId, BigDecimal dealValue) {
        cardRepository.findByLeadId(lead.getId()).ifPresent(card -> {
            CrmPipeline pipeline = pipelineRepository.findFirstByTenantIdAndDefaultPipelineTrue(tenantId).orElse(null);
            if (pipeline == null) {
                return;
            }
            List<CrmStage> stages = stageRepository.findByPipelineIdOrderByPositionAsc(pipeline.getId());
            CrmStage wonStage = stages.stream()
                .filter(stage -> "WON".equals(stage.getAutoTrigger()))
                .findFirst()
                .orElse(null);
            if (wonStage == null) {
                return;
            }
            card.setStage(wonStage);
            if (dealValue != null) {
                card.setValueAmount(dealValue);
            }
            card.setUpdatedAt(Instant.now());
            cardRepository.save(card);
        });
    }
}
