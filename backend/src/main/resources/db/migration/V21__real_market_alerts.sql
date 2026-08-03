-- Remove alertas de demonstração (TechMar / TechNova / Edificar etc.)
DELETE FROM trigger_alerts
WHERE alert_type IN ('NEW_CNPJ', 'HIRING_SIGNAL')
  AND (
    title IN ('Novo CNPJ na sua região', 'Novo CNPJ em Cabo Frio', 'Empresa contratando')
    OR description ILIKE '%publicou vagas de vendas%'
  );
