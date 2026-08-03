-- Corrige e-mail do fixture Daniel Felipe Lago de Lyra
UPDATE companies
SET email = 'danielfelipe.l.lyra@gmail.com',
    updated_at = NOW()
WHERE id = 'aaaaaaaa-bbbb-cccc-dddd-000000000003'
  AND email IS DISTINCT FROM 'danielfelipe.l.lyra@gmail.com';

UPDATE company_contacts
SET email = 'danielfelipe.l.lyra@gmail.com'
WHERE company_id = 'aaaaaaaa-bbbb-cccc-dddd-000000000003'
  AND email IS DISTINCT FROM 'danielfelipe.l.lyra@gmail.com';
