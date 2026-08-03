-- Criação idempotente de usuário e banco (PostgreSQL local ou servidor dedicado).
-- Executar como superusuário (postgres), ex.:
--   psql -U postgres -h localhost -p 5432 -f 00-init-role-database.sql

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'prospect') THEN
        CREATE ROLE prospect LOGIN PASSWORD 'prospect' CREATEDB;
    END IF;
END
$$;

SELECT 'CREATE DATABASE prospect_portal OWNER prospect'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'prospect_portal')\gexec

GRANT ALL PRIVILEGES ON DATABASE prospect_portal TO prospect;
