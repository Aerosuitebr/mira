package com.prospectportal.module.crm;

import com.prospectportal.module.client.ClientConversionService;
import com.prospectportal.module.client.entity.Client;
import com.prospectportal.module.crm.entity.CrmCard;
import com.prospectportal.module.crm.entity.CrmStage;
import com.prospectportal.module.crm.entity.Lead;
import com.prospectportal.module.crm.event.DealWonEvent;
import com.prospectportal.module.crm.repository.LeadRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CrmLifecycleService {

    private final ClientConversionService clientConversionService;
    private final LeadRepository leadRepository;
    private final ApplicationEventPublisher eventPublisher;

    public CrmLifecycleService(
        ClientConversionService clientConversionService,
        LeadRepository leadRepository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.clientConversionService = clientConversionService;
        this.leadRepository = leadRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void onCardMovedToStage(CrmCard card, CrmStage destinationStage) {
        if (destinationStage.getAutoTrigger() == null) {
            return;
        }

        Lead lead = card.getLead();
        switch (destinationStage.getAutoTrigger()) {
            case "WON" -> {
                Client client = clientConversionService.ensureFromCard(card);
                lead.setStatus("WON");
                lead.setUpdatedAt(Instant.now());
                leadRepository.save(lead);
                eventPublisher.publishEvent(new DealWonEvent(
                    card.getTenant().getId(),
                    lead.getId(),
                    client.getId(),
                    card.getId(),
                    card.getValueAmount()
                ));
            }
            case "LOST" -> {
                lead.setStatus("LOST");
                lead.setUpdatedAt(Instant.now());
                leadRepository.save(lead);
            }
            default -> {
                // outros gatilhos permanecem no CrmAutomationService (outreach)
            }
        }
    }
}
