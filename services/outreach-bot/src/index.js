import express from 'express';
import Redis from 'ioredis';
import { canOpenColdConversation, jobStep, nextColdAt, normalizePhone, reportDay, selectStep1Text } from './state.js';

const port = Number(process.env.PORT || 8090);
const redis = new Redis(process.env.REDIS_URL || 'redis://redis:6379');
const queueKey = process.env.OUTREACH_QUEUE_KEY || 'mira:outreach:jobs';
const eventQueueKey = process.env.OUTREACH_EVENT_QUEUE_KEY || 'mira:outreach:events';
const stateKey = 'mira:outreach:bot:state';
const conversationPrefix = 'mira:outreach:conversation:';
const app = express();
app.use(express.json());

const config = {
  paused: process.env.OUTREACH_BOT_PAUSED !== 'false',
  minIntervalSeconds: Number(process.env.OUTREACH_MIN_INTERVAL_SECONDS || 180),
  maxIntervalSeconds: Number(process.env.OUTREACH_MAX_INTERVAL_SECONDS || 300),
  dailyCap: Number(process.env.OUTREACH_DAILY_CAP || 15),
  timeZone: process.env.OUTREACH_TIMEZONE || 'America/Sao_Paulo',
  deliveryEnabled: process.env.OUTREACH_DELIVERY_ENABLED === 'true'
};
const serviceToken = process.env.BOT_SERVICE_TOKEN || process.env.MIRA_SERVICE_TOKEN || '';
const webhookSecret = process.env.EVOLUTION_WEBHOOK_SECRET || process.env.APP_EVOLUTION_WEBHOOK_SECRET || '';
const evolution = {
  baseUrl: (process.env.EVOLUTION_API_BASE_URL || process.env.APP_EVOLUTION_API_BASE_URL || '').replace(/\/$/, ''),
  apiKey: process.env.EVOLUTION_API_KEY || process.env.APP_EVOLUTION_API_KEY || '',
  instance: process.env.EVOLUTION_INSTANCE || process.env.APP_EVOLUTION_INSTANCE || ''
};
let processing = false;

function authorize(req, res, next) {
  if (!serviceToken || req.get('authorization') !== `Bearer ${serviceToken}`) {
    return res.status(401).json({ error: 'invalid-service-token' });
  }
  return next();
}

function evolutionMessageId(payload) {
  return payload?.key?.id || payload?.data?.key?.id || payload?.message?.key?.id || null;
}

async function state() {
  const saved = await redis.hgetall(stateKey);
  const today = reportDay(new Date(), config.timeZone);
  if (saved.day !== today) {
    saved.day = today;
    saved.sentToday = '0';
    saved.coldOpened = '0';
    saved.repliesReceived = '0';
    saved.step2Sent = '0';
    saved.failed = '0';
    saved.throttled = '0';
    await redis.hset(stateKey, {
      day: today,
      sentToday: '0',
      coldOpened: '0',
      repliesReceived: '0',
      step2Sent: '0',
      failed: '0',
      throttled: '0'
    });
  }
  const queue = await redis.llen(queueKey);
  const pendingEvents = await redis.llen(eventQueueKey);
  const sentToday = Number(saved.sentToday || 0);
  return {
    connected: true,
    paused: saved.paused == null ? config.paused : saved.paused === 'true',
    queue,
    pendingEvents,
    sentToday,
    remainingToday: Math.max(0, config.dailyCap - sentToday),
    restrictionDetected: saved.restrictionDetected === 'true',
    deliveryEnabled: config.deliveryEnabled,
    stage2ApprovalRequired: true,
    coldOpened: Number(saved.coldOpened || 0),
    repliesReceived: Number(saved.repliesReceived || 0),
    step2Sent: Number(saved.step2Sent || 0),
    failed: Number(saved.failed || 0),
    cadence: { minSeconds: config.minIntervalSeconds, maxSeconds: config.maxIntervalSeconds, dailyCap: config.dailyCap }
  };
}

async function postEvent(event) {
  if (!process.env.MIRA_API_URL || !serviceToken) throw new Error('MIRA API ou token de serviÃ§o nÃ£o configurado');
  const response = await fetch(`${process.env.MIRA_API_URL.replace(/\/$/, '')}/api/internal/outreach/events`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'X-Mira-Service-Token': serviceToken },
    body: JSON.stringify(event)
  });
  if (!response.ok) throw new Error(`MIRA events HTTP ${response.status}`);
}

async function emit(type, payload = {}) {
  const event = { type, occurredAt: new Date().toISOString(), ...payload };
  try {
    await postEvent(event);
  } catch (error) {
    await redis.rpush(eventQueueKey, JSON.stringify(event));
    console.error(`could not report ${type}; queued for retry`, error.message);
  }
}

async function flushEvents() {
  for (let index = 0; index < 25; index += 1) {
    const raw = await redis.lpop(eventQueueKey);
    if (!raw) return;
    try {
      await postEvent(JSON.parse(raw));
    } catch (error) {
      await redis.lpush(eventQueueKey, raw);
      console.error('could not flush outreach event', error.message);
      return;
    }
  }
}

async function incrementMetric(type) {
  const field = {
    STEP1_SENT: 'coldOpened',
    REPLY_RECEIVED: 'repliesReceived',
    STEP2_SENT: 'step2Sent',
    FAILED: 'failed',
    THROTTLED: 'throttled'
  }[type];
  if (field) await redis.hincrby(stateKey, field, 1);
}

async function sendReport() {
  if (!process.env.MIRA_API_URL || !serviceToken) return;
  const current = await state();
  try {
    await fetch(`${process.env.MIRA_API_URL.replace(/\/$/, '')}/api/internal/outreach/reports`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'X-Mira-Service-Token': serviceToken },
      body: JSON.stringify({
        generatedAt: new Date().toISOString(),
        queue: current.queue,
        paused: current.paused,
        sentToday: current.sentToday,
        remainingToday: current.remainingToday,
        coldOpened: Number((await redis.hget(stateKey, 'coldOpened')) || 0),
        repliesReceived: Number((await redis.hget(stateKey, 'repliesReceived')) || 0),
        step2Sent: Number((await redis.hget(stateKey, 'step2Sent')) || 0),
        failed: Number((await redis.hget(stateKey, 'failed')) || 0),
        throttled: Number((await redis.hget(stateKey, 'throttled')) || 0)
      })
    });
  } catch (error) {
    console.error('could not send outreach report', error.message);
  }
}

async function sendText(destination, text) {
  if (!evolution.baseUrl || !evolution.apiKey || !evolution.instance) {
    throw new Error('Evolution não configurada no outreach-bot');
  }
  const response = await fetch(`${evolution.baseUrl}/message/sendText/${encodeURIComponent(evolution.instance)}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', accept: 'application/json', apikey: evolution.apiKey },
    body: JSON.stringify({ number: normalizePhone(destination), text })
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const detail = JSON.stringify(body);
    const error = new Error(`Evolution HTTP ${response.status}: ${detail}`);
    error.rateLimited = response.status === 429 || detail.toLowerCase().includes('rate');
    throw error;
  }
  return body?.key?.id || body?.key?.messageId || body?.messageId || body?.id || null;
}

async function canOpenNextColdConversation() {
  const today = reportDay(new Date(), config.timeZone);
  const current = await redis.hgetall(stateKey);
  if (current.day !== today) await redis.hset(stateKey, { day: today, sentToday: '0' });
  return canOpenColdConversation(current.day === today ? current : {}, config);
}

async function processOne() {
  if (processing || !config.deliveryEnabled) return;
  const current = await state();
  if (current.paused) return;
  processing = true;
  try {
    const raw = await redis.lpop(queueKey);
    if (!raw) return;
    const job = JSON.parse(raw);
    const isStep2 = jobStep(job) === 'STEP2';
    const text = isStep2 ? job.step2Text : selectStep1Text(job);
  if (!isStep2 && !(await canOpenNextColdConversation())) {
      await redis.lpush(queueKey, raw);
      return;
    }
    if (!normalizePhone(job.phone) || !text) {
      await emit('SKIPPED', { messageId: job.messageId, reason: 'Contato sem WhatsApp ou texto' });
      return;
    }
    try {
      const providerMessageId = await sendText(job.phone, text);
      if (isStep2) {
        await emit('STEP2_SENT', { messageId: job.messageId, phone: normalizePhone(job.phone), providerMessageId, step2Text: job.step2Text });
        await incrementMetric('STEP2_SENT');
        return;
      }
      await redis.multi()
        .hset(stateKey, 'sentToday', String(current.sentToday + 1), 'nextColdAt', String(nextColdAt(config)))
        .set(`${conversationPrefix}${normalizePhone(job.phone)}`, JSON.stringify(job), 'EX', 60 * 60 * 24 * 14)
        .exec();
      await emit('STEP1_SENT', { messageId: job.messageId, phone: normalizePhone(job.phone), providerMessageId, step1Text: text });
      await incrementMetric('STEP1_SENT');
    } catch (error) {
      if (error.rateLimited) {
        await redis.lpush(queueKey, raw);
        await redis.hset(stateKey, 'restrictionDetected', 'true', 'paused', 'true');
        await emit('THROTTLED', { messageId: job.messageId, reason: error.message });
        await incrementMetric('THROTTLED');
      } else {
        await emit('FAILED', { messageId: job.messageId, reason: error.message });
        await incrementMetric('FAILED');
      }
    }
  } catch (error) {
    console.error('queue processing failed', error);
  } finally {
    processing = false;
  }
}

app.get('/health', async (_req, res) => {
  try { await redis.ping(); res.json({ status: 'ok' }); } catch { res.status(503).json({ status: 'redis-unavailable' }); }
});
app.get('/v1/status', authorize, async (_req, res) => res.json(await state()));
app.post('/v1/pause', authorize, async (_req, res) => { await redis.hset(stateKey, 'paused', 'true'); res.json(await state()); });
app.post('/v1/resume', authorize, async (_req, res) => { await redis.hset(stateKey, 'paused', 'false'); res.json(await state()); });
app.get('/v1/conversations', authorize, async (req, res) => {
  const number = normalizePhone(req.query.phone);
  const raw = number ? await redis.get(`${conversationPrefix}${number}`) : null;
  res.json({ phone: number, conversations: raw ? [JSON.parse(raw)] : [] });
});

// Evolution chama este endpoint público. O proxy HTTPS publica somente esta rota.
app.post('/webhooks/evolution', async (req, res) => {
  if (!webhookSecret || req.get('X-Webhook-Secret') !== webhookSecret) return res.sendStatus(401);
  const payload = req.body || {};
  const data = payload.data || payload;
  if (data?.key?.fromMe) return res.sendStatus(202);
  const sender = normalizePhone(data?.key?.remoteJid || data?.key?.participant || data?.sender || payload.sender || payload.from);
  const raw = sender ? await redis.get(`${conversationPrefix}${sender}`) : null;
  if (!raw) return res.sendStatus(202);
  const job = JSON.parse(raw);
  await emit('REPLY_RECEIVED', { messageId: job.messageId, phone: sender, providerMessageId: evolutionMessageId(payload) });
  await incrementMetric('REPLY_RECEIVED');
  // A resposta cria a revisÃ£o no MIRA. A etapa 2 sÃ³ Ã© enfileirada apÃ³s aprovaÃ§Ã£o humana.
  return res.sendStatus(202);
});

// A entrega é bloqueada em produção por OUTREACH_DELIVERY_ENABLED=false até liberar a conta.
setInterval(processOne, 1_000).unref();
setInterval(flushEvents, 15_000).unref();
setInterval(sendReport, 15 * 60 * 1_000).unref();
app.listen(port, () => console.log(`outreach-bot listening on ${port}, paused=${config.paused}`));
