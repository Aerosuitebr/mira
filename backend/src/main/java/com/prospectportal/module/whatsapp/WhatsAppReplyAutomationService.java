package com.prospectportal.module.whatsapp;

import com.prospectportal.module.evolution.EvolutionClient;
import com.prospectportal.module.outreach.OutreachSettingsService;
import com.prospectportal.module.outreach.OutreachBotQueueService;
import com.prospectportal.module.outreach.entity.OutreachMessage;
import com.prospectportal.module.outreach.repository.OutreachMessageRepository;
import com.prospectportal.module.prospect.ProspectCopyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import com.prospectportal.web.dto.FollowUpApprovalResponse;
import com.prospectportal.web.dto.FollowUpReviewItem;

/** Recebe uma resposta legítima e prepara (ou envia, quando explicitamente habilitado) a etapa 2. */
@Service
public class WhatsAppReplyAutomationService {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppReplyAutomationService.class);

    private final OutreachMessageRepository messageRepository;
    private final OutreachSettingsService settingsService;
    private final ProspectCopyBuilder copyBuilder;
    private final EvolutionClient evolutionClient;
    private final OutreachBotQueueService outreachBotQueueService;
    private final String publicBaseUrl;

    public WhatsAppReplyAutomationService(
        OutreachMessageRepository messageRepository,
        OutreachSettingsService settingsService,
        ProspectCopyBuilder copyBuilder,
        EvolutionClient evolutionClient,
        OutreachBotQueueService outreachBotQueueService,
        @Value("${app.public-base-url:http://localhost:4201}") String publicBaseUrl
    ) {
        this.messageRepository = messageRepository;
        this.settingsService = settingsService;
        this.copyBuilder = copyBuilder;
        this.evolutionClient = evolutionClient;
        this.outreachBotQueueService = outreachBotQueueService;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void registerReply(String senderPhone) {
        String recipient = EvolutionClient.cleanPhone(senderPhone);
        if (recipient.isBlank()) return;

        for (OutreachMessage firstStep : messageRepository.findLatestFirstStepSentTo(recipient)) {
            firstStep.setRepliedAt(Instant.now());
            messageRepository.save(firstStep);
            if (messageRepository.existsByReplyToMessageIdAndOutreachStep(firstStep.getId(), (short) 2)) continue;

            var brand = settingsService.resolveBrand(firstStep.getCampaign().getTenant().getId());
            OutreachMessage followUp = new OutreachMessage();
            followUp.setCampaign(firstStep.getCampaign());
            followUp.setLead(firstStep.getLead());
            followUp.setChannel("WHATSAPP");
            followUp.setBody(copyBuilder.whatsappFollowUp(firstStep.getLead().getCompany().getTradeName(), brand));
            followUp.setRecipient(recipient);
            followUp.setProspectJobId(firstStep.getProspectJobId());
            followUp.setReplyToMessageId(firstStep.getId());
            followUp.setOutreachStep((short) 2);
            followUp.setCreatedAt(Instant.now());
            followUp.setStatus("AWAITING_APPROVAL");
            followUp.setApprovalToken(UUID.randomUUID().toString());
            followUp.setApprovalExpiresAt(Instant.now().plus(Duration.ofDays(7)));

            messageRepository.save(followUp);
            notifyApprovers(followUp);
            return;
        }
    }

    @Transactional(readOnly = true)
    public FollowUpReviewItem reviewByToken(String token) {
        OutreachMessage message = requirePendingToken(token);
        return new FollowUpReviewItem(message.getId(), message.getLead().getCompany().getId(),
            displayName(message), message.getRecipient(), message.getBody(), message.getCreatedAt());
    }

    @Transactional
    public FollowUpApprovalResponse approveByToken(String token) {
        OutreachMessage message = requirePendingToken(token);
        message.setStatus("QUEUED_BOT");
        message.setApprovalApprovedAt(Instant.now());
        messageRepository.save(message);
        outreachBotQueueService.enqueueApprovedStep2(message);
        return new FollowUpApprovalResponse(message.getId(), "QUEUED_BOT", null);
    }

    private OutreachMessage requirePendingToken(String token) {
        OutreachMessage message = messageRepository.findByApprovalToken(token)
            .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Link não encontrado"));
        if (!"AWAITING_APPROVAL".equals(message.getStatus()) || message.getApprovalExpiresAt().isBefore(Instant.now()))
            throw new ResponseStatusException(org.springframework.http.HttpStatus.GONE, "Link expirado ou já utilizado");
        return message;
    }

    private void notifyApprovers(OutreachMessage followUp) {
        String link = publicBaseUrl + "/aprovar-abordagem/" + followUp.getApprovalToken();
        String notification = "Nova resposta de " + displayName(followUp) + " (" + followUp.getRecipient() + ").\n"
            + "Revise e autorize a etapa 2: " + link;
        var tenant = followUp.getCampaign().getTenant();
        java.util.stream.Stream.of(tenant.getOutreachApprovalRecipient1(), tenant.getOutreachApprovalRecipient2())
            .filter(number -> number != null && !number.isBlank())
            .distinct()
            .forEach(number -> {
                var result = evolutionClient.sendInternalNotification(number, notification);
                if (!result.success()) {
                    log.warn("RevisÃ£o da etapa 2 criada, mas o alerta para {} falhou: {}", number, result.error());
                }
            });
    }

    private static String displayName(OutreachMessage message) {
        String name = message.getLead().getCompany().getTradeName();
        return name != null && !name.isBlank() ? name : message.getLead().getCompany().getLegalName();
    }
}
