CREATE TABLE professional_listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id VARCHAR(160),
    source VARCHAR(80) NOT NULL DEFAULT 'RESOLVA_JATO',
    name VARCHAR(180) NOT NULL,
    occupation VARCHAR(160) NOT NULL,
    specialties TEXT,
    bio TEXT,
    email VARCHAR(255),
    whatsapp VARCHAR(40),
    phone VARCHAR(40),
    website VARCHAR(500),
    instagram VARCHAR(255),
    profile_image_url VARCHAR(1000),
    rating NUMERIC(3,2),
    review_count INTEGER NOT NULL DEFAULT 0,
    years_experience INTEGER,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    service_mode VARCHAR(30) NOT NULL DEFAULT 'IN_PERSON',
    neighborhood VARCHAR(160),
    city VARCHAR(160) NOT NULL,
    state VARCHAR(2) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location GEOGRAPHY(POINT, 4326) GENERATED ALWAYS AS
        (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED,
    available BOOLEAN NOT NULL DEFAULT TRUE,
    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_professional_source_external UNIQUE (source, external_id)
);

CREATE INDEX idx_professional_listings_location ON professional_listings USING GIST (location);
CREATE INDEX idx_professional_listings_occupation ON professional_listings USING GIN
    (to_tsvector('portuguese', occupation || ' ' || COALESCE(specialties, '') || ' ' || COALESCE(bio, '')));
CREATE INDEX idx_professional_listings_available ON professional_listings (available, state, city);

CREATE TABLE free_mira_contact_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(160) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_free_mira_contact_tenant UNIQUE (tenant_id)
);

COMMENT ON TABLE professional_listings IS 'Anúncios públicos de profissionais, sincronizáveis pelo Resolva Já.';
COMMENT ON TABLE free_mira_contact_usage IS 'Franquia gratuita única do MIRA: uma empresa ou um profissional, por e-mail ou WhatsApp.';
