-- flyway:executeInTransaction=false
-- Suporta paginação keyset por estado durante sincronização PostgreSQL -> Elasticsearch.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_companies_state_id
    ON companies (state, id);
