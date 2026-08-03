# Base CNPJ — Receita Federal (MIRA)

## Fonte oficial

- Portal: https://dados.gov.br/dados/conjuntos-dados/cadastro-nacional-da-pessoa-juridica---cnpj
- Arquivos: `https://arquivos.receitafederal.gov.br/dados/cnpj/dados_abertos_cnpj/`
- Tamanho típico: ~6 GB zip / ~20–30 GB CSV; índice pode passar de dezenas de GB

## Política neste projeto

| Camada | Onde |
|--------|------|
| Código / scripts | Git (este repo) |
| ZIPs/CSVs brutos | `.data/receita/` (gitignored) |
| Índice ao vivo | Postgres + Meilisearch (Docker / VPS) |
| Backup do índice | Object storage (S3/R2/Vultr) — **obrigatório** |

Não versionar a base no Git. Não depender só de SSD local.

## Fluxo

1. `npm run ingest:receita:download`
2. `npm run ingest:receita`
3. Agendar dump diário do índice para a nuvem
4. Atualização mensal quando a RFB publicar novo lote
