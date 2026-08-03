CREATE INDEX IF NOT EXISTS idx_companies_pending_zip_state
    ON companies (state, zip_code)
    WHERE geocoded = FALSE AND data_source = 'RECEITA_FEDERAL';
