CREATE INDEX IF NOT EXISTS idx_companies_state_cnae_main_active
    ON companies (state, cnae_main)
    WHERE COALESCE(registration_status, '02') = '02';
