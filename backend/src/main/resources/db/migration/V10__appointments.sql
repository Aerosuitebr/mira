CREATE TABLE appointments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL REFERENCES tenants(id),
    owner_user_id           UUID REFERENCES users(id),
    client_id               UUID REFERENCES clients(id),
    client_name             VARCHAR(300) NOT NULL,
    client_email            VARCHAR(320),
    client_phone            VARCHAR(50),
    client_company          VARCHAR(300),
    title                   VARCHAR(300) NOT NULL,
    description             TEXT,
    location                VARCHAR(500),
    starts_at               TIMESTAMPTZ NOT NULL,
    ends_at                 TIMESTAMPTZ,
    reminder_minutes_before INT NOT NULL DEFAULT 30,
    reminder_sent_at        TIMESTAMPTZ,
    status                  VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_appointments_tenant_starts ON appointments(tenant_id, starts_at DESC);
CREATE INDEX idx_appointments_reminder ON appointments(status, reminder_sent_at, starts_at)
    WHERE status = 'SCHEDULED' AND reminder_sent_at IS NULL;

ALTER TABLE trigger_alerts ALTER COLUMN company_id DROP NOT NULL;
ALTER TABLE trigger_alerts ADD COLUMN appointment_id UUID REFERENCES appointments(id);
CREATE INDEX idx_trigger_alerts_appointment ON trigger_alerts(appointment_id)
    WHERE appointment_id IS NOT NULL;
