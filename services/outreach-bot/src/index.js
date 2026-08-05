import express from 'express';
import Redis from 'ioredis';

const port = Number(process.env.PORT || 8090);
const redis = new Redis(process.env.REDIS_URL || 'redis://redis:6379');
const queueKey = process.env.OUTREACH_QUEUE_KEY || 'mira:outreach:jobs';
const stateKey = 'mira:outreach:bot:state';
const app = express();
app.use(express.json());

const config = {
  paused: process.env.OUTREACH_BOT_PAUSED !== 'false',
  minIntervalSeconds: Number(process.env.OUTREACH_MIN_INTERVAL_SECONDS || 180),
  maxIntervalSeconds: Number(process.env.OUTREACH_MAX_INTERVAL_SECONDS || 300),
  dailyCap: Number(process.env.OUTREACH_DAILY_CAP || 15)
};
const serviceToken = process.env.BOT_SERVICE_TOKEN || '';

function authorize(req, res, next) {
  if (!serviceToken || req.get('authorization') !== `Bearer ${serviceToken}`) {
    return res.status(401).json({ error: 'invalid-service-token' });
  }
  return next();
}

async function state() {
  const saved = await redis.hgetall(stateKey);
  const queue = await redis.llen(queueKey);
  return {
    connected: true,
    paused: saved.paused == null ? config.paused : saved.paused === 'true',
    queue,
    sentToday: Number(saved.sentToday || 0),
    remainingToday: Math.max(0, config.dailyCap - Number(saved.sentToday || 0)),
    restrictionDetected: saved.restrictionDetected === 'true',
    cadence: { minSeconds: config.minIntervalSeconds, maxSeconds: config.maxIntervalSeconds, dailyCap: config.dailyCap }
  };
}

app.get('/health', async (_req, res) => {
  try { await redis.ping(); res.json({ status: 'ok' }); } catch { res.status(503).json({ status: 'redis-unavailable' }); }
});
app.get('/v1/status', authorize, async (_req, res) => res.json(await state()));
app.post('/v1/pause', authorize, async (_req, res) => { await redis.hset(stateKey, 'paused', 'true'); res.json(await state()); });
app.post('/v1/resume', authorize, async (_req, res) => { await redis.hset(stateKey, 'paused', 'false'); res.json(await state()); });
app.get('/v1/conversations', authorize, async (req, res) => {
  const phone = String(req.query.phone || '');
  res.json({ phone, conversations: [] });
});

// Fase 1: nunca consome a fila. A ativação de consumo depende de OUTREACH_BOT_PAUSED=false
// e da implementação da Fase 2 (Evolution inbound + eventos autenticados no MIRA).
app.listen(port, () => console.log(`outreach-bot listening on ${port}, paused=${config.paused}`));
