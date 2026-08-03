CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Tenant demo
INSERT INTO tenants (id, name, plan_code, monthly_credits, credits_used)
VALUES ('11111111-1111-1111-1111-111111111111', 'Demo Comercial Ltda', 'PROFESSIONAL', 2000, 120);

-- User: demo@prospectportal.com / demo123
INSERT INTO users (id, tenant_id, email, password_hash, full_name, role)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'demo@prospectportal.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'Ana Comercial',
    'ADMIN'
);

INSERT INTO companies (cnpj, legal_name, trade_name, cnae_main, cnae_description, legal_nature, capital_social, opened_at, city, state, neighborhood, street, zip_code, latitude, longitude, location, estimated_revenue, website) VALUES
('12345678000190', 'Metalúrgica Horizonte Ltda', 'Metal Horizonte', '2511000', 'Fabricação de estruturas metálicas', 'Sociedade Empresária Limitada', 850000.00, '2015-03-12', 'São Paulo', 'SP', 'Mooca', 'Rua dos Metalúrgicos, 450', '03162010', -23.5505, -46.5963, ST_SetSRID(ST_MakePoint(-46.5963, -23.5505), 4326)::geography, 'MEDIUM', 'https://metalhorizonte.com.br'),
('23456789000181', 'Distribuidora Alfa Foods SA', 'Alfa Foods', '4639701', 'Comércio atacadista de produtos alimentícios', 'Sociedade Anônima Fechada', 3200000.00, '2010-08-22', 'São Paulo', 'SP', 'Vila Leopoldina', 'Av. Industrial, 1200', '05311000', -23.5280, -46.7350, ST_SetSRID(ST_MakePoint(-46.7350, -23.5280), 4326)::geography, 'LARGE', 'https://alfafoods.com.br'),
('34567890000172', 'TechNova Soluções Digitais Ltda', 'TechNova', '6201501', 'Desenvolvimento de programas de computador sob encomenda', 'Sociedade Empresária Limitada', 150000.00, '2019-01-05', 'Campinas', 'SP', 'Cambuí', 'Rua Barão de Jaguara, 880', '13023000', -22.9064, -47.0616, ST_SetSRID(ST_MakePoint(-47.0616, -22.9064), 4326)::geography, 'SMALL', 'https://technova.io'),
('45678901000163', 'LogTrans Transportes Ltda', 'LogTrans', '4930202', 'Transporte rodoviário de carga', 'Sociedade Empresária Limitada', 500000.00, '2012-11-30', 'Guarulhos', 'SP', 'Centro', 'Rod. Presidente Dutra, km 225', '07034000', -23.4628, -46.5333, ST_SetSRID(ST_MakePoint(-46.5333, -23.4628), 4326)::geography, 'MEDIUM', 'https://logtrans.com.br'),
('56789012000154', 'Clínica Vida Plena Ltda', 'Vida Plena', '8630503', 'Atividade médica ambulatorial', 'Sociedade Empresária Limitada', 200000.00, '2018-06-18', 'São Paulo', 'SP', 'Pinheiros', 'Rua Teodoro Sampaio, 1020', '05406050', -23.5675, -46.6930, ST_SetSRID(ST_MakePoint(-46.6930, -23.5675), 4326)::geography, 'SMALL', 'https://vidaplena.med.br'),
('67890123000145', 'Construtora Edificar SA', 'Edificar', '4120400', 'Construção de edifícios', 'Sociedade Anônima Fechada', 5000000.00, '2008-04-02', 'São Paulo', 'SP', 'Brooklin', 'Av. Eng. Luís Carlos Berrini, 550', '04571000', -23.5950, -46.6860, ST_SetSRID(ST_MakePoint(-46.6860, -23.5950), 4326)::geography, 'LARGE', 'https://edificar.com.br'),
('78901234000136', 'Auto Peças Rápido Ltda', 'Peças Rápido', '4530703', 'Comércio a varejo de peças e acessórios novos', 'Sociedade Empresária Limitada', 300000.00, '2016-09-14', 'Osasco', 'SP', 'Centro', 'Av. dos Autonomistas, 1400', '06090010', -23.5329, -46.7919, ST_SetSRID(ST_MakePoint(-46.7919, -23.5329), 4326)::geography, 'MEDIUM', 'https://pecasrapido.com.br'),
('89012345000127', 'Gráfica Express Print Ltda', 'Express Print', '1811301', 'Impressão de material para uso publicitário', 'Sociedade Empresária Limitada', 120000.00, '2014-02-28', 'São Paulo', 'SP', 'Brás', 'Rua Oriente, 350', '03015000', -23.5470, -46.6160, ST_SetSRID(ST_MakePoint(-46.6160, -23.5470), 4326)::geography, 'SMALL', 'https://expressprint.com.br');

INSERT INTO company_contacts (company_id, full_name, role_title, email, phone, whatsapp, linkedin_url, source, confidence, enriched_at)
SELECT c.id, 'Carlos Mendes', 'Diretor Comercial', 'carlos.mendes@' || split_part(replace(c.website, 'https://', ''), '/', 1),
       '(11) 98765-4321', '5511987654321', 'https://linkedin.com/in/carlos-mendes', 'WEB_SCRAPE', 85, NOW()
FROM companies c WHERE c.cnpj = '12345678000190';

INSERT INTO company_contacts (company_id, full_name, role_title, email, whatsapp, linkedin_url, source, confidence, enriched_at)
SELECT c.id, 'Mariana Souza', 'CEO', 'mariana@alfafoods.com.br', '5511998877665', 'https://linkedin.com/in/mariana-souza', 'WEB_SCRAPE', 92, NOW()
FROM companies c WHERE c.cnpj = '23456789000181';

INSERT INTO outreach_templates (tenant_id, name, channel, subject, body_template) VALUES
('11111111-1111-1111-1111-111111111111', 'Apresentação B2B', 'EMAIL', 'Parceria estratégica para {{companyName}}',
 'Olá {{contactName}},\n\nAnalisamos o perfil da {{companyName}} no segmento de {{cnaeDescription}} e acreditamos que podemos agregar valor imediato.\n\nPodemos conversar esta semana?\n\nAbraços,\n{{senderName}}');

INSERT INTO crm_pipelines (id, tenant_id, name, is_default) VALUES
('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Funil Comercial', TRUE);

INSERT INTO crm_stages (id, pipeline_id, name, position, color, auto_trigger) VALUES
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '33333333-3333-3333-3333-333333333333', 'Área de Trabalho', 0, '#94a3b8', NULL),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '33333333-3333-3333-3333-333333333333', 'Contatado', 1, '#3b82f6', 'MESSAGE_SENT'),
('cccccccc-cccc-cccc-cccc-cccccccccccc', '33333333-3333-3333-3333-333333333333', 'Proposta Enviada', 2, '#8b5cf6', 'PROPOSAL_SENT'),
('dddddddd-dddd-dddd-dddd-dddddddddddd', '33333333-3333-3333-3333-333333333333', 'Em Negociação', 3, '#f59e0b', 'REPLY_RECEIVED'),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '33333333-3333-3333-3333-333333333333', 'Fechado', 4, '#22c55e', 'WON'),
('ffffffff-ffff-ffff-ffff-ffffffffffff', '33333333-3333-3333-3333-333333333333', 'Perdido', 5, '#ef4444', 'LOST');

INSERT INTO trigger_alerts (tenant_id, company_id, alert_type, title, description)
SELECT '11111111-1111-1111-1111-111111111111', c.id, 'NEW_CNPJ', 'Novo CNPJ na sua região',
       c.trade_name || ' abriu em ' || c.city || '/' || c.state
FROM companies c WHERE c.cnpj = '34567890000172';

INSERT INTO trigger_alerts (tenant_id, company_id, alert_type, title, description)
SELECT '11111111-1111-1111-1111-111111111111', c.id, 'HIRING_SIGNAL', 'Empresa contratando',
       c.trade_name || ' publicou vagas de vendas — sinal de orçamento ativo'
FROM companies c WHERE c.cnpj = '67890123000145';
