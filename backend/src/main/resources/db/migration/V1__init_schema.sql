CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE tenants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    plan_code       VARCHAR(50) NOT NULL DEFAULT 'ESSENTIAL',
    monthly_credits INTEGER NOT NULL DEFAULT 500,
    credits_used    INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(200) NOT NULL,
    role            VARCHAR(50) NOT NULL DEFAULT 'SELLER',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_tenant ON users(tenant_id);

CREATE TABLE companies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cnpj                VARCHAR(14) NOT NULL UNIQUE,
    legal_name          VARCHAR(500) NOT NULL,
    trade_name          VARCHAR(500),
    cnae_main           VARCHAR(10) NOT NULL,
    cnae_description    VARCHAR(500),
    legal_nature        VARCHAR(100),
    capital_social      NUMERIC(18, 2),
    opened_at           DATE,
    city                VARCHAR(120) NOT NULL,
    state               VARCHAR(2) NOT NULL,
    neighborhood        VARCHAR(120),
    street              VARCHAR(255),
    zip_code            VARCHAR(8),
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    location            GEOGRAPHY(POINT, 4326),
    estimated_revenue   VARCHAR(50),
    website             VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_companies_cnae ON companies(cnae_main);
CREATE INDEX idx_companies_city_state ON companies(city, state);
CREATE INDEX idx_companies_location ON companies USING GIST(location);
CREATE INDEX idx_companies_legal_name_trgm ON companies USING gin (legal_name gin_trgm_ops);

CREATE TABLE company_contacts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id      UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    full_name       VARCHAR(200),
    role_title      VARCHAR(200),
    email           VARCHAR(255),
    phone           VARCHAR(30),
    whatsapp        VARCHAR(30),
    linkedin_url    VARCHAR(500),
    source          VARCHAR(100),
    confidence      SMALLINT NOT NULL DEFAULT 0,
    enriched_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_company_contacts_company ON company_contacts(company_id);

CREATE TABLE leads (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    company_id      UUID NOT NULL REFERENCES companies(id),
    owner_user_id   UUID REFERENCES users(id),
    status          VARCHAR(50) NOT NULL DEFAULT 'NEW',
    score           INTEGER NOT NULL DEFAULT 0,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, company_id)
);

CREATE INDEX idx_leads_tenant_status ON leads(tenant_id, status);

CREATE TABLE outreach_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            VARCHAR(200) NOT NULL,
    channel         VARCHAR(20) NOT NULL,
    subject         VARCHAR(500),
    body_template   TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE outreach_campaigns (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            VARCHAR(200) NOT NULL,
    channel         VARCHAR(20) NOT NULL,
    template_id     UUID REFERENCES outreach_templates(id),
    status          VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    scheduled_at    TIMESTAMPTZ,
    sent_count      INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE outreach_messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id     UUID NOT NULL REFERENCES outreach_campaigns(id) ON DELETE CASCADE,
    lead_id         UUID NOT NULL REFERENCES leads(id),
    channel         VARCHAR(20) NOT NULL,
    subject         VARCHAR(500),
    body            TEXT NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    sent_at         TIMESTAMPTZ,
    opened_at       TIMESTAMPTZ,
    replied_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE crm_pipelines (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    name            VARCHAR(200) NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE crm_stages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id     UUID NOT NULL REFERENCES crm_pipelines(id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    position        INTEGER NOT NULL,
    color           VARCHAR(20) NOT NULL DEFAULT '#6366f1',
    auto_trigger    VARCHAR(50)
);

CREATE TABLE crm_cards (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    pipeline_id     UUID NOT NULL REFERENCES crm_pipelines(id),
    stage_id        UUID NOT NULL REFERENCES crm_stages(id),
    lead_id         UUID NOT NULL REFERENCES leads(id),
    title           VARCHAR(300) NOT NULL,
    value_amount    NUMERIC(18, 2),
    position        INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, lead_id)
);

CREATE INDEX idx_crm_cards_stage ON crm_cards(stage_id, position);

CREATE TABLE trigger_alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    company_id      UUID NOT NULL REFERENCES companies(id),
    alert_type      VARCHAR(50) NOT NULL,
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    read            BOOLEAN NOT NULL DEFAULT FALSE,
    triggered_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trigger_alerts_tenant ON trigger_alerts(tenant_id, read, triggered_at DESC);
