-- Empresas demo em Cabo Frio/RJ para validar busca regional
INSERT INTO companies (cnpj, legal_name, trade_name, cnae_main, cnae_description, legal_nature, capital_social, opened_at, city, state, neighborhood, street, zip_code, latitude, longitude, location, estimated_revenue, website) VALUES
('11222333000144', 'Hotel Praia Azul Ltda', 'Praia Azul Hotel', '5510801', 'Hotéis', 'Sociedade Empresária Limitada', 1200000.00, '2011-05-20', 'Cabo Frio', 'RJ', 'Braga', 'Av. do Contorno, 1200', '28907000', -22.8794, -42.0186, ST_SetSRID(ST_MakePoint(-42.0186, -22.8794), 4326)::geography, 'LARGE', 'https://praiaazulhotel.com.br'),
('22333444000155', 'Restaurante Maré Alta Ltda', 'Maré Alta', '5611201', 'Restaurantes e similares', 'Sociedade Empresária Limitada', 350000.00, '2016-02-14', 'Cabo Frio', 'RJ', 'Centro', 'Rua Luís Alves, 45', '28905000', -22.8850, -42.0270, ST_SetSRID(ST_MakePoint(-42.0270, -22.8850), 4326)::geography, 'MEDIUM', 'https://marealta.com.br'),
('33444555000166', 'Clínica Saúde Total Cabo Frio Ltda', 'Saúde Total', '8630503', 'Atividade médica ambulatorial', 'Sociedade Empresária Limitada', 280000.00, '2019-08-03', 'Cabo Frio', 'RJ', 'Unamar', 'Rua dos Flamboyants, 300', '28928000', -22.8910, -42.0420, ST_SetSRID(ST_MakePoint(-42.0420, -22.8910), 4326)::geography, 'MEDIUM', 'https://saudetotalcabofrio.com.br'),
('44555666000177', 'Auto Center Litoral Ltda', 'Auto Center Litoral', '4530703', 'Comércio a varejo de peças e acessórios novos', 'Sociedade Empresária Limitada', 420000.00, '2014-11-08', 'Cabo Frio', 'RJ', 'Portinho', 'Av. Teixeira e Souza, 890', '28915000', -22.8720, -42.0050, ST_SetSRID(ST_MakePoint(-42.0050, -22.8720), 4326)::geography, 'MEDIUM', 'https://autocenterlitoral.com.br'),
('55666777000188', 'TechMar Digital Ltda', 'TechMar', '6201501', 'Desenvolvimento de programas de computador sob encomenda', 'Sociedade Empresária Limitada', 95000.00, '2020-01-15', 'Cabo Frio', 'RJ', 'Centro', 'Rua Marechal Floriano, 78', '28905000', -22.8835, -42.0255, ST_SetSRID(ST_MakePoint(-42.0255, -22.8835), 4326)::geography, 'SMALL', 'https://techmar.digital');

INSERT INTO trigger_alerts (tenant_id, company_id, alert_type, title, description)
SELECT '11111111-1111-1111-1111-111111111111', c.id, 'NEW_CNPJ', 'Novo CNPJ em Cabo Frio',
       c.trade_name || ' abriu em Cabo Frio/RJ'
FROM companies c WHERE c.cnpj = '55666777000188';
