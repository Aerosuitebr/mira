ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS outreach_approval_recipient_1 VARCHAR(30),
    ADD COLUMN IF NOT EXISTS outreach_approval_recipient_2 VARCHAR(30);
