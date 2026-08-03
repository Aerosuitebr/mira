-- Prospecção automática: jobs + metadados reais de envio (WhatsApp/e-mail)

CREATE TABLE prospect_jobs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    campaign_id         UUID REFERENCES outreach_campaigns(id),
    name                VARCHAR(200) NOT NULL,
    cnae                VARCHAR(20),
    state               VARCHAR(2),
    city                VARCHAR(120),
    keyword             VARCHAR(200),
    company_limit       INTEGER NOT NULL DEFAULT 20,
    status              VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    test_mode           BOOLEAN NOT NULL DEFAULT TRUE,
    dry_run             BOOLEAN NOT NULL DEFAULT FALSE,
    found_count         INTEGER NOT NULL DEFAULT 0,
    enriched_count      INTEGER NOT NULL DEFAULT 0,
    queued_count        INTEGER NOT NULL DEFAULT 0,
    wa_sent             INTEGER NOT NULL DEFAULT 0,
    email_sent          INTEGER NOT NULL DEFAULT 0,
    failed_count        INTEGER NOT NULL DEFAULT 0,
    error_detail        TEXT,
    next_dispatch_at    TIMESTAMPTZ,
    wa_paused_until     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prospect_jobs_tenant_created ON prospect_jobs (tenant_id, created_at DESC);
CREATE INDEX idx_prospect_jobs_status ON prospect_jobs (status);

ALTER TABLE outreach_messages
    ADD COLUMN IF NOT EXISTS provider VARCHAR(40),
    ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(200),
    ADD COLUMN IF NOT EXISTS error_detail TEXT,
    ADD COLUMN IF NOT EXISTS fallback_of UUID REFERENCES outreach_messages(id),
    ADD COLUMN IF NOT EXISTS prospect_job_id UUID REFERENCES prospect_jobs(id),
    ADD COLUMN IF NOT EXISTS recipient VARCHAR(320);

CREATE INDEX idx_outreach_messages_pending
    ON outreach_messages (status, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outreach_messages_job
    ON outreach_messages (prospect_job_id);
