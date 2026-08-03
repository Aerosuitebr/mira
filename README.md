# MIRA

Busca B2B da Aerosuite: encontre **empresas para prospectar** ou **profissionais próximos** do local do serviço.

| Item | Valor |
|------|--------|
| Produto | MIRA (B2B) |
| URL prod | https://search.aerosuite.com.br |
| Dev | http://localhost:4201 |
| Entrada do hub | `/escolher-busca?origem=resolva-jato` |
| Org | [Aerosuitebr](https://github.com/Aerosuitebr) |

## Ecossistema

- **Resolva Jato** (`resolva-jato`) — hub de ferramentas; linka via `NEXT_PUBLIC_MIRA_URL`
- **Aerosuite** (`aerosuite`) — marca e ops
- **MIRA** (este repo) — busca + índice Receita Federal / CNPJ

## Stack

- Next.js 15 (App Router) na porta **4201**
- PostgreSQL + Meilisearch
- Scripts de download/ingestão dos dados abertos da RFB

## Como rodar

```bash
cp .env.example .env
npm install
docker compose up -d postgres meilisearch
npm run db:push
npm run dev
```

Abra http://localhost:4201/escolher-busca

## Base da Receita Federal

A base indexada **não** fica no Git (`.data/`). Ver `docs/RECEITA-FEDERAL.md`.

## Status

Repo recriado após perda do SSD. O app legado (Angular :4201) não estava no Git público; este scaffold é a base oficial para reconstruir o produto.
