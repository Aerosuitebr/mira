# Prospect Portal — B2B All-in-One

Portal SaaS que unifica **prospecção de empresas**, **enriquecimento de contatos**, **abordagem multi-canal** e **CRM Kanban** em um único fluxo:

`Descobrir → Enriquecer → Abordar → Gerenciar`

## Stack

| Camada | Tecnologia |
|--------|------------|
| Backend | Java 21, Spring Boot 3.4, Spring Security (JWT), Flyway |
| Frontend | Angular 19 (standalone components) |
| Banco principal | **PostgreSQL 16 + PostGIS** (busca geoespacial e CRM) |
| Cache | Redis (produção) / cache simples (perfil `dev`) |

> Para escalar a base completa de CNPJs (50M+ registros), a arquitetura prevê indexação em **Elasticsearch** como próxima fase — o MVP usa PostgreSQL com índices GIST/GIN.

## Estrutura

```
b2b-prospect-portal/
├── backend/          API REST modular
├── frontend/         SPA Angular
└── docker-compose.yml
```

## Pré-requisitos

- Java 21
- Maven 3.9+
- Node.js 20+
- Docker Desktop

## Subir infraestrutura

```bash
docker compose up -d
```

Para iniciar toda a aplicação de uma vez no Windows, execute `start-dev.cmd` na raiz do projeto.

Serviços:
- PostgreSQL/PostGIS: `localhost:5433`
- Redis: `localhost:6380`
- Elasticsearch: `localhost:9201`

## Dados reais (grátis) — pipeline CNPJ

Estrutura local em **`C:\prospect-portal-data`** (ver README na pasta).

```powershell
# 1. Baixar arquivos abertos Receita Federal (parcial ou completo)
cd d:\Desenvolvimento\b2b-prospect-portal\scripts
.\download-rf.ps1 -ReferenceMonth 2026-01 -Files Municipios,Cnaes,Empresas0,Estabelecimentos0

# Dataset completo (Empresas0-9 + Estabelecimentos0-9, ~20 GB)
.\download-rf-full.ps1 -ReferenceMonth 2026-01

# 2. Extrair ZIPs
.\extract-rf.ps1

# 3. Subir Elasticsearch (primeira vez)
cd ..
docker compose up -d

# 4. Importar com geocode + Elasticsearch
.\scripts\start-rf-import.ps1 -States RJ

# Pipeline completa (download + extrair + importar)
.\scripts\run-rf-full-pipeline.ps1 -States RJ

# Somente geocode + indexar (apos importacao, sem reprocessar CSVs)
.\scripts\post-import-enrich.ps1 -States RJ
```

| Componente | Tecnologia | Custo |
|------------|------------|-------|
| Base CNPJ | Receita Federal (dados abertos) | Grátis |
| Geocodificação | Nominatim + ViaCEP | Grátis |
| Busca rápida | Elasticsearch (Docker) | Grátis |
| Contatos | E-mail/telefone da própria RF | Grátis |

Endpoints admin (requer usuário **ADMIN**):
- `POST /api/admin/import/rf` — inicia importação
- `POST /api/admin/import/rf/enrich` — geocodifica + indexa Elasticsearch (sem CSV)
- `GET /api/admin/import/rf/status` — acompanha progresso
- `GET /api/admin/import/data-paths` — caminhos configurados

## Portas (sem conflito com outros projetos locais)

| Serviço | Porta | Observação |
|---------|-------|------------|
| Frontend Angular | **4201** | Seu outro app pode continuar em `4200` |
| Backend API | **8082** | Seu outro app pode continuar em `8081` |
| PostgreSQL/PostGIS | **5433** | |
| Redis | **6380** | |

## Backend

```bash
cd backend
mvn -s .mvn/settings.xml spring-boot:run
```

API: `http://localhost:8082`

### Usuário demo

- **E-mail:** `demo@prospectportal.com`
- **Senha:** `demo123`

No perfil `dev`, a senha demo é regravada automaticamente na inicialização.

## Frontend

```bash
cd frontend
npm install
npm start
```

App: `http://localhost:4201` (proxy `/api` → `http://localhost:8082`)

## Módulos da API

| Módulo | Endpoints principais |
|--------|----------------------|
| Auth | `POST /api/auth/login` |
| Discovery | `GET /api/discovery/companies`, `GET /api/discovery/companies/geo` |
| Enrichment | `POST /api/enrichment/enrich` |
| Outreach | `POST /api/outreach/ai-copy`, `POST /api/outreach/campaigns/bulk`, `GET /api/outreach/channels`, `POST /api/outreach/test-email` |
| Prospect | `POST /api/prospect/jobs`, `GET /api/prospect/jobs`, `POST /api/prospect/jobs/{id}/pause\|resume` |
| CRM | `GET /api/crm/board` |
| Alerts | `GET /api/alerts` |

## Prospecção automática (WhatsApp + e-mail Aero Suite)

Fluxo: segmento/região → busca → enrich → WhatsApp (Evolution) com throttle anti-ban → fallback e-mail premium SMTP.

### Configuração

1. Copie SMTP do AeroSuite para o `.env` do portal (`SPRING_MAIL_*`, senha de app Google).
2. Evolution:
   - **Local:** `docker compose -f docker-compose.yml -f docker-compose.evolution.yml up -d`
   - **Produção (mesmo host Vultr do Aero Suite):** use [`.env.production.example`](.env.production.example) + [`docker-compose.production.yml`](docker-compose.production.yml). A Evolution já existe em `127.0.0.1:18082` — não subir outra. Instância: `aerosuite-default` (ou `mira-prospect`).
3. Variáveis principais:

| Variável | Valor típico |
|----------|----------------|
| `APP_OUTREACH_TEST_MODE` | `false` (produção: envia para leads reais) |
| `APP_OUTREACH_TEST_EMAIL` | só se `TEST_MODE=true` (redireciona e-mails) |
| `APP_EVOLUTION_ENABLED` | `true` |
| `APP_EVOLUTION_INSTANCE` | `aerosuite-default` |

### Checklist anti-ban WhatsApp

- Janela comercial seg–sex 09h–18h (America/Sao_Paulo)
- Intervalo 45–120s com jitter entre envios
- Cap horário 5 / diário 30 (warm-up 10→20→30)
- Checagem `whatsappNumbers` antes do envio
- Em rate-limit: pausa WA e fallback e-mail
- Em `test-mode`: e-mails redirecionados; WA em massa omitido

### Produção

1. `APP_OUTREACH_TEST_MODE=false` no `.env`
2. Evolution conectada (`aerosuite-default`)
3. Na tela **Prospectar**, deixe **Modo seguro** desmarcado
4. Inicie a prospecção automática com limite adequado e caps diários ativos
5. Para voltar a um sandbox: marque **Modo seguro** ou `APP_OUTREACH_TEST_MODE=true` + `APP_OUTREACH_TEST_EMAIL`

## Modelo de negócio (SaaS)

Planos previstos no schema (`ESSENTIAL`, `PROFESSIONAL`, `ENTERPRISE`) com controle de créditos mensais por tenant.

## Próximos passos sugeridos

1. Importar todos os arquivos `Empresas0..9` + `Estabelecimentos0..9` para cobertura nacional
2. LLM local (Ollama) ou OpenAI no gerador de copy
3. Webhooks de leitura/resposta WhatsApp
4. Billing/recorrência (Stripe ou gateway local)

## Licença

Projeto privado — uso interno/comercial a definir.
