package com.prospectportal.module.outreach;

import com.prospectportal.common.entity.Tenant;
import com.prospectportal.module.crm.entity.CrmCard;
import com.prospectportal.module.crm.entity.CrmPipeline;
import com.prospectportal.module.crm.entity.CrmStage;
import com.prospectportal.module.crm.entity.Lead;
import com.prospectportal.module.crm.repository.CrmCardRepository;
import com.prospectportal.module.crm.repository.CrmPipelineRepository;
import com.prospectportal.module.crm.repository.CrmStageRepository;
import com.prospectportal.module.crm.repository.LeadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CrmAutomationService {

    private final CrmPipelineRepository pipelineRepository;
    private final CrmStageRepository stageRepository;
    private final CrmCardRepository cardRepository;
    private final LeadRepository leadRepository;

    public CrmAutomationService(
        CrmPipelineRepository pipelineRepository,
        CrmStageRepository stageRepository,
        CrmCardRepository cardRepository,
        LeadRepository leadRepository
    ) {
        this.pipelineRepository = pipelineRepository;
        this.stageRepository = stageRepository;
        this.cardRepository = cardRepository;
        this.leadRepository = leadRepository;
    }

    @Transactional
    public void onMessageSent(Lead lead, String companyName) {
        Tenant tenant = lead.getTenant();
        CrmPipeline pipeline = pipelineRepository.findFirstByTenantIdAndDefaultPipelineTrue(tenant.getId())
            .orElseThrow();

        CrmStage proposalStage = stageRepository.findByPipelineIdOrderByPositionAsc(pipeline.getId()).stream()
            .filter(stage -> "PROPOSAL_SENT".equals(stage.getAutoTrigger()))
            .findFirst()
            .orElse(stageRepository.findByPipelineIdOrderByPositionAsc(pipeline.getId()).stream()
                .filter(stage -> "MESSAGE_SENT".equals(stage.getAutoTrigger()))
                .findFirst()
                .orElse(stageRepository.findByPipelineIdOrderByPositionAsc(pipeline.getId()).get(1)));

        cardRepository.findByPipelineIdOrderByStageIdAscPositionAsc(pipeline.getId()).stream()
            .filter(card -> card.getLead().getId().equals(lead.getId()))
            .findFirst()
            .ifPresentOrElse(
                card -> {
                    card.setStage(proposalStage);
                    card.setUpdatedAt(Instant.now());
                    cardRepository.save(card);
                },
                () -> {
                    CrmCard card = new CrmCard();
                    card.setTenant(tenant);
                    card.setPipeline(pipeline);
                    card.setStage(proposalStage);
                    card.setLead(lead);
                    card.setTitle(companyName);
                    card.setPosition(nextPosition(pipeline.getId(), proposalStage.getId()));
                    card.setCreatedAt(Instant.now());
                    card.setUpdatedAt(Instant.now());
                    cardRepository.save(card);
                }
            );

        lead.setStatus("CONTACTED");
        lead.setUpdatedAt(Instant.now());
        leadRepository.save(lead);
    }

    private int nextPosition(java.util.UUID pipelineId, java.util.UUID stageId) {
        return (int) cardRepository.findByPipelineIdOrderByStageIdAscPositionAsc(pipelineId).stream()
            .filter(card -> card.getStage().getId().equals(stageId))
            .count();
    }
}
