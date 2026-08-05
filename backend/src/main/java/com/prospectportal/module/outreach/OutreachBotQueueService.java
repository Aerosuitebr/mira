package com.prospectportal.module.outreach;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prospectportal.module.outreach.entity.OutreachMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Publica somente trabalho do fluxo WhatsApp protegido. O MIRA não envia o contato frio. */
@Service
public class OutreachBotQueueService {
    private static final String QUEUE = "mira:outreach:jobs";
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public OutreachBotQueueService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void enqueue(OutreachMessage message, String companyName, String step2Text) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "messageId", message.getId().toString(),
                "campaignId", message.getCampaign().getId().toString(),
                "leadId", message.getLead().getId().toString(),
                "companyId", message.getLead().getCompany().getId().toString(),
                "phone", message.getRecipient() == null ? "" : message.getRecipient(),
                "companyName", companyName,
                "step1Text", message.getBody(),
                "step2Text", step2Text,
                "approachId", "DIRECT"
            ));
            redis.opsForList().rightPush(QUEUE, payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Não foi possível enfileirar outreach-bot", ex);
        }
    }

    /** Etapa 2 só entra aqui após resposta do lead e aprovação humana. */
    public void enqueueApprovedStep2(OutreachMessage message) {
        try {
            String companyName = java.util.Objects.requireNonNullElse(message.getLead().getCompany().getTradeName(), "");
            String payload = objectMapper.writeValueAsString(Map.of(
                "type", "STEP2",
                "messageId", message.getId().toString(),
                "campaignId", message.getCampaign().getId().toString(),
                "leadId", message.getLead().getId().toString(),
                "companyId", message.getLead().getCompany().getId().toString(),
                "phone", message.getRecipient() == null ? "" : message.getRecipient(),
                "companyName", companyName,
                "step2Text", message.getBody(),
                "approachId", "APPROVED_FOLLOW_UP"
            ));
            redis.opsForList().rightPush(QUEUE, payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Não foi possível enfileirar etapa 2 no outreach-bot", ex);
        }
    }
}
