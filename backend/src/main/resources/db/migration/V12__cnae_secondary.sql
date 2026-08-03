ALTER TABLE companies ADD COLUMN IF NOT EXISTS cnae_secondary VARCHAR(2000);

CREATE INDEX IF NOT EXISTS idx_companies_cnae_secondary ON companies (cnae_secondary)
    WHERE cnae_secondary IS NOT NULL AND cnae_secondary <> '';
