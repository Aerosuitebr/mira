-- Extensões exigidas pelo schema (PostGIS + busca textual).
-- Executar conectado ao banco prospect_portal.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
