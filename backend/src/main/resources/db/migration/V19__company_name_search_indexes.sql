-- Índice leve para busca por prefixo de CNPJ (instantânea).
-- Índice trgm em trade_name é pesado (~16M linhas) e roda via script admin, não no boot.
CREATE INDEX IF NOT EXISTS idx_companies_cnpj_prefix ON companies (cnpj varchar_pattern_ops);
