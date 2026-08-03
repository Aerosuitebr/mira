ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS evolution_instance_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS evolution_connection_state VARCHAR(40),
    ADD COLUMN IF NOT EXISTS evolution_connected_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS evolution_phone VARCHAR(30);

CREATE UNIQUE INDEX IF NOT EXISTS uq_tenants_evolution_instance
    ON tenants (evolution_instance_name)
    WHERE evolution_instance_name IS NOT NULL;
