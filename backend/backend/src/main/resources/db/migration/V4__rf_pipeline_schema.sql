-- Campos adicionais alinhados ao layout Receita Federal
ALTER TABLE companies ADD COLUMN IF NOT EXISTS registration_status VARCHAR(2);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS company_size_code VARCHAR(2);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS municipality_code VARCHAR(10);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS data_source VARCHAR(50) DEFAULT 'SEED';
ALTER TABLE companies ADD COLUMN IF NOT EXISTS geocoded BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_companies_state_city ON companies(state, city);
CREATE INDEX IF NOT EXISTS idx_companies_registration ON companies(registration_status);
CREATE INDEX IF NOT EXISTS idx_companies_geocoded ON companies(geocoded) WHERE geocoded = FALSE;

-- Lookup municípios RF (codigo -> nome)
CREATE TABLE rf_municipios (
    code        VARCHAR(10) PRIMARY KEY,
    name        VARCHAR(200) NOT NULL
);

-- Staging empresas (CNPJ básico = 8 primeiros dígitos)
CREATE TABLE rf_empresas (
    cnpj_basico         VARCHAR(8) PRIMARY KEY,
    legal_name          VARCHAR(500) NOT NULL,
    legal_nature_code   VARCHAR(10),
    capital_social      NUMERIC(18, 2),
    company_size_code   VARCHAR(2),
    loaded_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rf_empresas_loaded ON rf_empresas(loaded_at);

-- Descrições CNAE
CREATE TABLE rf_cnaes (
    code        VARCHAR(10) PRIMARY KEY,
    description VARCHAR(500) NOT NULL
);

-- Cache geocodificação (grátis: ViaCEP + Nominatim)
CREATE TABLE geo_cache (
    cache_key   VARCHAR(500) PRIMARY KEY,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    provider    VARCHAR(50) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Controle de jobs de importação
CREATE TABLE import_jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type        VARCHAR(50) NOT NULL,
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    params_json     TEXT,
    processed_rows  BIGINT NOT NULL DEFAULT 0,
    inserted_rows   BIGINT NOT NULL DEFAULT 0,
    skipped_rows    BIGINT NOT NULL DEFAULT 0,
    error_message   TEXT,
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_import_jobs_status ON import_jobs(status, created_at DESC);
