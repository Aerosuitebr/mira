package com.prospectportal.module.outreach;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prospectportal.module.outreach.entity.OutreachMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Publica somente trabalho do fluxo WhatsApp protegido. O MIRA não envia o contato frio. */
@Service
public class OutreachBotQueueService {
    private static final String COLD_QUEUE = "mira:outreach:jobs";
    private static final String PRIORITY_QUEUE = "mira:outreach:priority";
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
            redis.opsForList().rightPush(COLD_QUEUE, payload);
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
            redis.opsForList().rightPush(PRIORITY_QUEUE, payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Não foi possível enfileirar etapa 2 no outreach-bot", ex);
        }
    }

    public void requeue(OutreachMessage message) {
        if (message.getOutreachStep() == 2) {
            enqueueApprovedStep2(message);
            return;
        }
        enqueue(message, message.getLead().getCompany().getTradeName(), message.getBody());
    }

    /** Atualiza o snapshot ainda pendente sem alterar a posição na fila. */
    @SuppressWarnings("unchecked")
    public void updateQueuedText(OutreachMessage message) {
        for (String queue : List.of(COLD_QUEUE, PRIORITY_QUEUE)) {
            List<String> jobs = redis.opsForList().range(queue, 0, -1);
            if (jobs == null) continue;
            for (int index = 0; index < jobs.size(); index++) {
                String raw = jobs.get(index);
                try {
                    Map<String, Object> payload = objectMapper.readValue(raw, Map.class);
                    if (!message.getId().toString().equals(String.valueOf(payload.get("messageId")))) continue;
                    payload.put(message.getOutreachStep() == 2 ? "step2Text" : "step1Text", message.getBody());
                    redis.opsForList().set(queue, index, objectMapper.writeValueAsString(payload));
                    return;
                } catch (JsonProcessingException ignored) {
                    // Item inválido será tratado pelo consumidor e não bloqueia a edição.
                }
            }
        }
    }

    /** Atualiza a etapa 2 de todos os itens frios ainda pendentes da campanha. */
    @SuppressWarnings("unchecked")
    public void updateQueuedFollowUp(UUID campaignId, String template) {
        List<String> jobs = redis.opsForList().range(COLD_QUEUE, 0, -1);
        if (jobs == null) return;
        for (int index = 0; index < jobs.size(); index++) {
            try {
                Map<String, Object> payload = objectMapper.readValue(jobs.get(index), Map.class);
                if (!campaignId.toString().equals(String.valueOf(payload.get("campaignId")))) continue;
                String companyName = String.valueOf(payload.getOrDefault("companyName", ""));
                payload.put("step2Text", template.replace("{{empresa}}", companyName));
                redis.opsForList().set(COLD_QUEUE, index, objectMapper.writeValueAsString(payload));
            } catch (JsonProcessingException ignored) {
                // Um item inválido não impede a atualização dos demais.
            }
        }
    }
}
