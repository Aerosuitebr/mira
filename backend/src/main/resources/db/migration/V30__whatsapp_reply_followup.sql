-- Fase 2: guarda a etapa da conversa para que a segunda mensagem só exista
-- depois de uma resposta recebida pelo webhook.
ALTER TABLE outreach_messages
    ADD COLUMN IF NOT EXISTS outreach_step SMALLINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS reply_to_message_id UUID REFERENCES outreach_messages(id);

CREATE INDEX IF NOT EXISTS idx_outreach_messages_recipient_status
    ON outreach_messages (recipient, channel, status);
