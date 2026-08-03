ALTER TABLE companies ADD COLUMN IF NOT EXISTS web_contactable BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE companies ADD COLUMN IF NOT EXISTS web_probe_status VARCHAR(20);
ALTER TABLE companies ADD COLUMN IF NOT EXISTS web_probed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_companies_active_contactable
    ON companies (state, web_contactable)
    WHERE registration_status = '02' AND web_contactable = TRUE;
