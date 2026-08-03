package com.prospectportal.module.crm;

import com.prospectportal.common.entity.Tenant;
import com.prospectportal.common.repository.TenantRepository;
import com.prospectportal.module.crm.entity.CrmCard;
import com.prospectportal.module.crm.entity.CrmPipeline;
import com.prospectportal.module.crm.entity.CrmStage;
import com.prospectportal.module.crm.entity.Lead;
import com.prospectportal.module.crm.repository.CrmCardRepository;
import com.prospectportal.module.crm.repository.CrmPipelineRepository;
import com.prospectportal.module.crm.repository.CrmStageRepository;
import com.prospectportal.module.crm.repository.LeadRepository;
import com.prospectportal.module.discovery.entity.Company;
import com.prospectportal.module.discovery.repository.CompanyRepository;
import com.prospectportal.security.AuthContext;
import com.prospectportal.web.dto.CreateLeadsRequest;
import com.prospectportal.web.dto.KanbanBoardResponse;
import com.prospectportal.web.dto.KanbanCardResponse;
import com.prospectportal.web.dto.KanbanStageResponse;
import com.prospectportal.web.dto.LeadResponse;
import com.prospectportal.web.dto.MoveCardRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class CrmService {

    private final AuthContext authContext;
    private final TenantRepository tenantRepository;
    private final CompanyRepository companyRepository;
    private final LeadRepository leadRepository;
    private final CrmPipelineRepository pipelineRepository;
    private final CrmStageRepository stageRepository;
    private final CrmCardRepository cardRepository;
    private final CrmLifecycleService crmLifecycleService;

    public CrmService(
        AuthContext authContext,
        TenantRepository tenantRepository,
        CompanyRepository companyRepository,
        LeadRepository leadRepository,
        CrmPipelineRepository pipelineRepository,
        CrmStageRepository stageRepository,
        CrmCardRepository cardRepository,
        CrmLifecycleService crmLifecycleService
    ) {
        this.authContext = authContext;
        this.tenantRepository = tenantRepository;
        this.companyRepository = companyRepository;
        this.leadRepository = leadRepository;
        this.pipelineRepository = pipelineRepository;
        this.stageRepository = stageRepository;
        this.cardRepository = cardRepository;
        this.crmLifecycleService = crmLifecycleService;
    }

    @Transactional(readOnly = true)
    public KanbanBoardResponse getBoard() {
        UUID tenantId = authContext.tenantId();
        CrmPipeline pipeline = pipelineRepository.findFirstByTenantIdAndDefaultPipelineTrue(tenantId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Pipeline não configurado"));

        List<CrmStage> stages = stageRepository.findByPipelineIdOrderByPositionAsc(pipeline.getId());
        List<CrmCard> cards = cardRepository.findBoardCards(pipeline.getId());

        List<KanbanStageResponse> stageResponses = stages.stream().map(stage -> {
            List<KanbanCardResponse> stageCards = cards.stream()
                .filter(card -> card.getStage().getId().equals(stage.getId()))
                .map(this::toCardResponse)
                .toList();
            return new KanbanStageResponse(stage.getId(), stage.getName(), stage.getPosition(), stage.getColor(), stageCards);
        }).toList();

        return new KanbanBoardResponse(pipeline.getId(), pipeline.getName(), stageResponses);
    }

    @Transactional
    public KanbanCardResponse moveCard(UUID cardId, MoveCardRequest request) {
        CrmCard card = cardRepository.findById(cardId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Card não encontrado"));

        if (!card.getTenant().getId().equals(authContext.tenantId())) {
            throw new ResponseStatusException(NOT_FOUND, "Card não encontrado");
        }

        stageRepository.findById(request.stageId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Estágio não encontrado"));

        Instant now = Instant.now();
        int updated = cardRepository.updateStageAndPosition(cardId, request.stageId(), request.position(), now);
        if (updated == 0) {
            throw new ResponseStatusException(NOT_FOUND, "Card não encontrado");
        }

        card = cardRepository.findById(cardId).orElseThrow();
        CrmStage destinationStage = stageRepository.findById(request.stageId()).orElseThrow();
        crmLifecycleService.onCardMovedToStage(card, destinationStage);
        return toCardResponse(card);
    }

    @Transactional(readOnly = true)
    public List<LeadResponse> listLeads() {
        UUID tenantId = authContext.tenantId();
        return leadRepository.findByTenantWithCompany(tenantId).stream()
            .map(lead -> {
                Company company = lead.getCompany();
                return new LeadResponse(
                    lead.getId(),
                    company.getId(),
                    company.getTradeName() != null ? company.getTradeName() : company.getLegalName(),
                    lead.getStatus(),
                    lead.getScore()
                );
            })
            .toList();
    }

    @Transactional
    public List<LeadResponse> createLeads(CreateLeadsRequest request) {
        UUID tenantId = authContext.tenantId();
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tenant não encontrado"));

        List<LeadResponse> created = new ArrayList<>();
        for (UUID companyId : request.companyIds()) {
            if (leadRepository.findByTenantIdAndCompanyId(tenantId, companyId).isPresent()) {
                continue;
            }
            Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Empresa não encontrada"));

            Lead lead = new Lead();
            lead.setTenant(tenant);
            lead.setCompany(company);
            lead.setStatus("NEW");
            lead.setScore(40);
            lead.setCreatedAt(Instant.now());
            lead.setUpdatedAt(Instant.now());
            lead = leadRepository.save(lead);

            created.add(new LeadResponse(
                lead.getId(),
                company.getId(),
                company.getTradeName() != null ? company.getTradeName() : company.getLegalName(),
                lead.getStatus(),
                lead.getScore()
            ));
        }
        return created;
    }

    private KanbanCardResponse toCardResponse(CrmCard card) {
        Company company = card.getLead().getCompany();
        return new KanbanCardResponse(
            card.getId(),
            card.getLead().getId(),
            card.getTitle(),
            company.getTradeName() != null ? company.getTradeName() : company.getLegalName(),
            company.getCity(),
            company.getState(),
            card.getValueAmount(),
            card.getPosition(),
            card.getCreatedAt(),
            card.getLead().getOwner() != null ? card.getLead().getOwner().getFullName() : authContext.currentUser().fullName()
        );
    }
}
