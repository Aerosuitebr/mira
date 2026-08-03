-- Branding de envio por tenant (PF/PJ): remetente + imagem para WA e e-mail.

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS outreach_sender_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS brand_image_base64 TEXT,
    ADD COLUMN IF NOT EXISTS brand_image_mime VARCHAR(40),
    ADD COLUMN IF NOT EXISTS brand_image_file_name VARCHAR(120);
