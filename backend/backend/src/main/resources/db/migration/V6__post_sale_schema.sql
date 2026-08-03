-- Seção 01: Carteira de clientes
CREATE TABLE clients (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    lead_id             UUID REFERENCES leads(id),
    company_id            UUID NOT NULL REFERENCES companies(id),
    owner_user_id       UUID REFERENCES users(id),
    legal_name          VARCHAR(500) NOT NULL,
    trade_name          VARCHAR(500),
    document            VARCHAR(20),
    email               VARCHAR(255),
    phone               VARCHAR(30),
    city                VARCHAR(120),
    state               VARCHAR(2),
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    service_status      VARCHAR(30) NOT NULL DEFAULT 'NONE',
    lifetime_value      NUMERIC(18, 2) NOT NULL DEFAULT 0,
    contracted_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_clients_tenant_company UNIQUE (tenant_id, company_id)
);

CREATE INDEX idx_clients_tenant_status ON clients(tenant_id, status);
CREATE INDEX idx_clients_tenant_ltv ON clients(tenant_id, lifetime_value DESC);
CREATE INDEX idx_clients_contracted_at ON clients(tenant_id, contracted_at DESC);

-- Seção 02: Propostas e orçamentos
CREATE TABLE proposals (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    lead_id             UUID REFERENCES leads(id),
    client_id           UUID REFERENCES clients(id),
    created_by_user_id  UUID REFERENCES users(id),
    title               VARCHAR(300) NOT NULL,
    total_amount        NUMERIC(18, 2) NOT NULL DEFAULT 0,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    payment_terms       TEXT,
    validity_days       INTEGER NOT NULL DEFAULT 15,
    approval_token      UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    approved_at         TIMESTAMPTZ,
    rejected_at         TIMESTAMPTZ,
    signer_document     VARCHAR(20),
    signer_name         VARCHAR(200),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_proposals_tenant_status ON proposals(tenant_id, status);
CREATE INDEX idx_proposals_lead ON proposals(lead_id);

CREATE TABLE proposal_items (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id         UUID NOT NULL REFERENCES proposals(id) ON DELETE CASCADE,
    description         VARCHAR(500) NOT NULL,
    quantity            NUMERIC(12, 2) NOT NULL DEFAULT 1,
    unit_price          NUMERIC(18, 2) NOT NULL DEFAULT 0,
    sort_order          INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_proposal_items_proposal ON proposal_items(proposal_id, sort_order);

-- Seção 03: Projetos / serviços pós-venda
CREATE TABLE projects (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    client_id           UUID NOT NULL REFERENCES clients(id),
    proposal_id         UUID REFERENCES proposals(id),
    name                VARCHAR(300) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'NOT_STARTED',
    progress_percent    INTEGER NOT NULL DEFAULT 0,
    owner_user_id       UUID REFERENCES users(id),
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_projects_tenant_status ON projects(tenant_id, status);
CREATE INDEX idx_projects_client ON projects(client_id);

CREATE TABLE project_milestones (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id              UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title                   VARCHAR(300) NOT NULL,
    description             TEXT,
    status                  VARCHAR(30) NOT NULL DEFAULT 'WAITING',
    position                INTEGER NOT NULL DEFAULT 0,
    due_at                  TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    requires_client_approval BOOLEAN NOT NULL DEFAULT FALSE,
    client_approved_at      TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_project_milestones_project ON project_milestones(project_id, position);

CREATE TABLE project_deliverables (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id          UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    milestone_id        UUID REFERENCES project_milestones(id) ON DELETE SET NULL,
    file_name           VARCHAR(500) NOT NULL,
    file_url            VARCHAR(1000) NOT NULL,
    visible_to_client   BOOLEAN NOT NULL DEFAULT TRUE,
    uploaded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seção 04: tokens de acesso externo (portal / proposta)
CREATE TABLE portal_access_tokens (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES tenants(id),
    token               UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    resource_type       VARCHAR(30) NOT NULL,
    resource_id         UUID NOT NULL,
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_portal_tokens_resource ON portal_access_tokens(resource_type, resource_id);
