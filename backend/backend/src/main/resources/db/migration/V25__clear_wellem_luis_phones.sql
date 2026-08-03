-- Fixtures de teste: Wellem e Luis sem telefone.
-- Na abordagem com canal WhatsApp: envia WA onde houver telefone;
-- Wellem e Luis caem no fallback de e-mail.

UPDATE companies
SET phone = NULL,
    updated_at = NOW()
WHERE id IN (
    'aaaaaaaa-bbbb-cccc-dddd-000000000001',
    'aaaaaaaa-bbbb-cccc-dddd-000000000004'
);

UPDATE companies
SET email = 'danielfelipe.l.lyra@gmail.com',
    updated_at = NOW()
WHERE id = 'aaaaaaaa-bbbb-cccc-dddd-000000000003'
  AND email IS DISTINCT FROM 'danielfelipe.l.lyra@gmail.com';

DELETE FROM company_contacts
WHERE company_id IN (
    'aaaaaaaa-bbbb-cccc-dddd-000000000001',
    'aaaaaaaa-bbbb-cccc-dddd-000000000002',
    'aaaaaaaa-bbbb-cccc-dddd-000000000003',
    'aaaaaaaa-bbbb-cccc-dddd-000000000004'
);

INSERT INTO company_contacts (
    company_id, full_name, role_title, email, phone, whatsapp, source, confidence, enriched_at
) VALUES
(
    'aaaaaaaa-bbbb-cccc-dddd-000000000001',
    'Wellem Mello de Lyra',
    'Decisor',
    'wellemlyra@gmail.com',
    NULL,
    NULL,
    'FLOW_TEST',
    99,
    NOW()
),
(
    'aaaaaaaa-bbbb-cccc-dddd-000000000002',
    'Thiago Lago de Lyra',
    'Decisor',
    'thiagolyra18@gmail.com',
    NULL,
    NULL,
    'FLOW_TEST',
    99,
    NOW()
),
(
    'aaaaaaaa-bbbb-cccc-dddd-000000000003',
    'Daniel Felipe Lago de Lyra',
    'Decisor',
    'danielfelipe.l.lyra@gmail.com',
    '21978309389',
    '5521978309389',
    'FLOW_TEST',
    99,
    NOW()
),
(
    'aaaaaaaa-bbbb-cccc-dddd-000000000004',
    'Luis Henrique Nascimento',
    'Decisor',
    'henri.geel21@gmail.com',
    NULL,
    NULL,
    'FLOW_TEST',
    99,
    NOW()
);
