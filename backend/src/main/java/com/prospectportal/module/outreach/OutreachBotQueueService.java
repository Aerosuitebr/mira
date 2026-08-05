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
}
