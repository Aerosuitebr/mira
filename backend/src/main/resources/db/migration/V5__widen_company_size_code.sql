-- Alguns registros RF trazem porte com mais de 2 caracteres (dados inconsistentes no CSV).
ALTER TABLE rf_empresas
    ALTER COLUMN company_size_code TYPE VARCHAR(10);

ALTER TABLE companies
    ALTER COLUMN company_size_code TYPE VARCHAR(10);
