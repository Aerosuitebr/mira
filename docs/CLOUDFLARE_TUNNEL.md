# Cloudflare Tunnel — MIRA (extensão Aero Suite)

O MIRA compartilha o **mesmo tunnel Cloudflare** do Aero Suite e usa o subdomínio **`search`**.

| App | Hostname | Porta local |
|-----|----------|-------------|
| Aero Suite | `app.aerosuite.app` | `8081` |
| **MIRA** | **`search.aerosuite.app`** | **`8083`** |

## Fluxo

```
Usuário → https://search.aerosuite.app
       → Cloudflare Tunnel (cloudflared)
       → http://127.0.0.1:8083  (nginx mira-tunnel-proxy)
       → /      → Angular :4201
       → /api/* → Spring Boot :8082
```

## Subir localmente (dev)

```powershell
# 1. Infra + app
.\start-dev.cmd

# 2. Proxy único para o tunnel
docker compose -f docker-compose.tunnel.yml up -d

# 3. Registrar DNS + instruções
.\scripts\deploy\setup-mira-tunnel.ps1
```

## Ingress no tunnel (produção / Vultr)

Edite `/etc/cloudflared/config.yml` e inclua **antes** do `http_status:404`:

```yaml
  - hostname: search.aerosuite.app
    service: http://127.0.0.1:8083
    originRequest:
      httpHostHeader: search.aerosuite.app

  - hostname: search.aerosuite.com.br
    service: http://127.0.0.1:8083
    originRequest:
      httpHostHeader: search.aerosuite.com.br
```

Modelo completo: `scripts/deploy/cloudflared-ingress-mira.yml`

Reinicie o serviço:

```bash
sudo systemctl restart cloudflared
```

## DNS

```bash
cloudflared tunnel route dns 6d599ea8-2354-4c3c-9968-5ded651c92fc search.aerosuite.app
cloudflared tunnel route dns 6d599ea8-2354-4c3c-9968-5ded651c92fc search.aerosuite.com.br
```

## Variáveis de ambiente (.env)

```env
PUBLIC_BASE_URL=https://search.aerosuite.app
CORS_ALLOWED_ORIGINS=http://localhost:4201,https://search.aerosuite.app,https://search.aerosuite.com.br
```

Reinicie o backend após alterar.

## Evolution WhatsApp (mesmo host do Aero Suite)

Em produção a Evolution **já roda com o Aero Suite** em `127.0.0.1:18082` (não expor na internet).

1. Copie [`.env.production.example`](../.env.production.example) → `.env.production` no servidor.
2. Preencha `APP_EVOLUTION_API_KEY` com o mesmo `EVOLUTION_API_KEY` do Aero Suite.
3. Use a instância já conectada (`aerosuite-default`) ou crie `mira-prospect` na Evolution.
4. Suba infra MIRA:

```bash
# no servidor Vultr, pasta do MIRA
cp .env.production.example .env.production
# edite senhas / API key
docker compose -f docker-compose.yml -f docker-compose.production.yml up -d
# API JVM no host carrega .env + .env.production (mesmo padrão do start-dev)
```

| Onde a API MIRA roda | `APP_EVOLUTION_API_BASE_URL` |
|----------------------|------------------------------|
| Processo no host (padrão atual) | `http://127.0.0.1:18082` |
| Container Docker | `http://host.docker.internal:18082` ou `http://aerosuite-evolution-api:8080` (rede externa) |

Não use `docker-compose.evolution.yml` em produção — ele é só para Evolution local isolada.

## Checklist

- [ ] `docker compose up -d` (Postgres, Redis, ES)
- [ ] Backend em `:8082`, frontend em `:4201`
- [ ] `docker compose -f docker-compose.tunnel.yml up -d` (proxy `:8083`)
- [ ] `curl -I http://localhost:8083` retorna 200
- [ ] Ingress `search.*` → `8083` no tunnel
- [ ] Tunnel **Healthy** no Zero Trust
- [ ] CORS/PUBLIC_BASE_URL atualizados
- [ ] Evolution Aero Suite em `:18082` + `APP_EVOLUTION_*` no `.env.production`
- [ ] `GET /api/outreach/channels` mostra WhatsApp conectado

## Prefixo `/search` no mesmo domínio do app?

É possível (`app.aerosuite.app/search`), mas exige `baseHref` no Angular e ajustes no nginx.  
**Recomendado:** subdomínio `search.aerosuite.app` (sem mudança no código).
