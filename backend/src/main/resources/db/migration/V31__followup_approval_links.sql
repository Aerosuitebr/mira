ALTER TABLE outreach_messages
    ADD COLUMN IF NOT EXISTS approval_token VARCHAR(80),
    ADD COLUMN IF NOT EXISTS approval_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approval_approved_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_outreach_messages_approval_token
    ON outreach_messages (approval_token)
    WHERE approval_token IS NOT NULL;
