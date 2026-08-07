import express from 'express';
import Redis from 'ioredis';
import { applyTimeGreeting, jobStep, nextColdAt, normalizePhone, reportDay, selectStep1Text } from './state.js';

const port = Number(process.env.PORT || 8090);
const redis = new Redis(process.env.REDIS_URL || 'redis://redis:6379');
const queueKey = process.env.OUTREACH_QUEUE_KEY || 'mira:outreach:jobs';
const priorityQueueKey = process.env.OUTREACH_PRIORITY_QUEUE_KEY || 'mira:outreach:priority';
const processingColdKey = `${queueKey}:processing`;
const processingPriorityKey = `${priorityQueueKey}:processing`;
const deadLetterKey = process.env.OUTREACH_DEAD_LETTER_KEY || 'mira:outreach:dead';
const pausedCampaignsKey = 'mira:outreach:paused-campaigns';
const cancelledCampaignsKey = 'mira:outreach:cancelled-campaigns';
const parkedCampaignPrefix = 'mira:outreach:parked-campaign:';
const eventQueueKey = process.env.OUTREACH_EVENT_QUEUE_KEY || 'mira:outreach:events';
const stateKey = 'mira:outreach:bot:state';
const conversationPrefix = 'mira:outreach:conversation:';
const dailyQuotaPrefix = 'mira:outreach:daily-quota:';
const nextSendPrefix = 'mira:outreach:next-send:';
const HARD_DAILY_CAP = 15;
const BUSINESS_START_MINUTE = 9 * 60;
const BUSINESS_END_MINUTE = 18 * 60;
const app = express();
app.use(express.json());

const config = {
  paused: process.env.OUTREACH_BOT_PAUSED !== 'false',
  minIntervalSeconds: Number(process.env.OUTREACH_MIN_INTERVAL_SECONDS || 180),
  maxIntervalSeconds: Number(process.env.OUTREACH_MAX_INTERVAL_SECONDS || 300),
  // Limite de segurança: nenhuma configuração pode elevar a franquia acima de 15.
  dailyCap: Math.min(HARD_DAILY_CAP, Math.max(1, Number(process.env.OUTREACH_DAILY_CAP || HARD_DAILY_CAP))),
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
const publicBaseUrl = (process.env.PUBLIC_BASE_URL || '').replace(/\/$/, '');
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

function quotaKey(instance, day = reportDay(new Date(), config.timeZone)) {
  return `${dailyQuotaPrefix}${day}:${encodeURIComponent(String(instance || evolution.instance || 'unconfigured').trim())}`;
}

function connectionKey(instance) {
  return encodeURIComponent(String(instance || evolution.instance || 'unconfigured').trim());
}

function businessClock(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: config.timeZone,
    hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
  }).formatToParts(now);
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  const minute = Number(values.hour) * 60 + Number(values.minute) + Number(values.second) / 60;
  return {
    minute,
    withinWindow: minute >= BUSINESS_START_MINUTE && minute < BUSINESS_END_MINUTE,
    millisecondsUntilOpen: minute < BUSINESS_START_MINUTE
      ? (BUSINESS_START_MINUTE - minute) * 60_000
      : ((24 * 60 - minute) + BUSINESS_START_MINUTE) * 60_000,
    millisecondsUntilClose: Math.max(0, (BUSINESS_END_MINUTE - minute) * 60_000)
  };
}

async function sendWindow(instance) {
  const clock = businessClock();
  const nextSendAt = Number((await redis.get(`${nextSendPrefix}${connectionKey(instance)}`)) || 0);
  return {
    withinBusinessHours: clock.withinWindow,
    businessHours: '09:00-18:00',
    nextSendAt: nextSendAt || null,
    allowedNow: clock.withinWindow && Date.now() >= nextSendAt
  };
}

async function scheduleNextSend(instance) {
  const quota = await dailyQuota(instance);
  const clock = businessClock();
  const remainingMessages = Math.max(1, quota.remainingToday);
  let delay;
  if (!clock.withinWindow) {
    delay = clock.millisecondsUntilOpen + Math.random() * 20 * 60_000;
  } else {
    // Divide o expediente restante em espaÃ§os e adiciona variaÃ§Ã£o de 60% a 140%.
    const baseGap = clock.millisecondsUntilClose / (remainingMessages + 1);
    delay = Math.max(3 * 60_000, baseGap * (0.6 + Math.random() * 0.8));
    delay = Math.min(delay, Math.max(3 * 60_000, clock.millisecondsUntilClose - 60_000));
  }
  await redis.set(`${nextSendPrefix}${connectionKey(instance)}`, String(Date.now() + Math.floor(delay)), 'EX', 60 * 60 * 48);
}

async function dailyQuota(instance) {
  const day = reportDay(new Date(), config.timeZone);
  const sentToday = Number((await redis.get(quotaKey(instance, day))) || 0);
  const window = await sendWindow(instance);
  return {
    day,
    sentToday,
    dailyLimit: config.dailyCap,
    remainingToday: Math.max(0, config.dailyCap - sentToday),
    exhausted: sentToday >= config.dailyCap,
    ...window
  };
}

async function reserveDailySlot(instance) {
  const key = quotaKey(instance);
  const result = await redis.eval(
    `local current = tonumber(redis.call('GET', KEYS[1]) or '0')
     if current >= tonumber(ARGV[1]) then return 0 end
     current = redis.call('INCR', KEYS[1])
     if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
     if current > tonumber(ARGV[1]) then redis.call('DECR', KEYS[1]); return 0 end
     return current`,
    1, key, config.dailyCap, 60 * 60 * 72
  );
  return Number(result) > 0;
}

async function releaseDailySlot(instance) {
  await redis.eval(
    `local current = tonumber(redis.call('GET', KEYS[1]) or '0')
     if current > 0 then return redis.call('DECR', KEYS[1]) end
     return 0`,
    1, quotaKey(instance)
  );
}

async function state(instance) {
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
  const step1Queue = await redis.llen(queueKey);
  const step2Queue = await redis.llen(priorityQueueKey);
  const processing = (await redis.llen(processingColdKey)) + (await redis.llen(processingPriorityKey));
  const deadLetter = await redis.llen(deadLetterKey);
  const pendingEvents = await redis.llen(eventQueueKey);
  const quota = await dailyQuota(instance);
  return {
    connected: true,
    paused: saved.paused == null ? config.paused : saved.paused === 'true',
    queue: step1Queue + step2Queue + processing,
    step1Queue,
    step2Queue,
    processing,
    deadLetter,
    pendingEvents,
    sentToday: quota.sentToday,
    remainingToday: quota.remainingToday,
    dailyLimit: quota.dailyLimit,
    quotaDay: quota.day,
    quotaExhausted: quota.exhausted,
    restrictionDetected: saved.restrictionDetected === 'true',
    pausedReason: saved.pausedReason || null,
    deliveryEnabled: config.deliveryEnabled,
    stage2ApprovalRequired: true,
    coldOpened: Number(saved.coldOpened || 0),
    repliesReceived: Number(saved.repliesReceived || 0),
    step2Sent: Number(saved.step2Sent || 0),
    failed: Number(saved.failed || 0),
    nextColdAt: Number(saved.nextColdAt || 0) || null,
    estimatedDays: Math.ceil(step1Queue / Math.max(1, config.dailyCap)),
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

async function sendText(destination, text, requestedInstance) {
  const instance = String(requestedInstance || evolution.instance || '').trim();
  if (!evolution.baseUrl || !evolution.apiKey || !instance) {
    throw new Error('Evolution não configurada no outreach-bot');
  }
  const response = await fetch(`${evolution.baseUrl}/message/sendText/${encodeURIComponent(instance)}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', accept: 'application/json', apikey: evolution.apiKey },
    body: JSON.stringify({ number: normalizePhone(destination), text })
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const detail = JSON.stringify(body);
    const error = new Error(`Evolution HTTP ${response.status}: ${detail}`);
    error.rateLimited = response.status === 429 || detail.toLowerCase().includes('rate');
    error.connectionUnavailable = response.status >= 500 && /connection\s*closed|disconnected|not connected|instance.*close/i.test(detail);
    error.invalidRecipient = response.status === 400 && /"exists"\s*:\s*false/i.test(detail);
    throw error;
  }
  return body?.key?.id || body?.key?.messageId || body?.messageId || body?.id || null;
}

async function canOpenNextColdConversation() {
  const today = reportDay(new Date(), config.timeZone);
  const current = await redis.hgetall(stateKey);
  if (current.day !== today) await redis.hset(stateKey, { day: today, sentToday: '0' });
  // A franquia Ã© validada por conexÃ£o em reserveDailySlot; aqui fica apenas a cadÃªncia.
  return Date.now() >= Number(current.nextColdAt || 0);
}

async function processOne() {
  if (processing || !config.deliveryEnabled) return;
  const current = await state();
  if (current.paused) return;
  processing = true;
  try {
    let sourceKey = priorityQueueKey;
    let processingKey = processingPriorityKey;
    let raw = await takeEligible(sourceKey, processingKey);
    if (!raw) {
      if (!(await canOpenNextColdConversation())) return;
      sourceKey = queueKey;
      processingKey = processingColdKey;
      raw = await takeEligible(sourceKey, processingKey);
    }
    if (!raw) return;
    const job = JSON.parse(raw);
    if (await redis.sismember(cancelledCampaignsKey, String(job.campaignId || ''))) {
      await redis.lrem(processingKey, 1, raw);
      return;
    }
    if (await redis.sismember(pausedCampaignsKey, String(job.campaignId || ''))) {
      await parkJob(job.campaignId, sourceKey, processingKey, raw);
      return;
    }
    const isStep2 = jobStep(job) === 'STEP2';
    const quotaInstance = String(job.evolutionInstance || evolution.instance || '').trim();
    const text = applyTimeGreeting(isStep2 ? job.step2Text : selectStep1Text(job), config.timeZone);
    if (!normalizePhone(job.phone) || !text) {
      await emit('SKIPPED', { messageId: job.messageId, reason: 'Contato sem WhatsApp ou texto' });
      await redis.lrem(processingKey, 1, raw);
      return;
    }
    if (!(await reserveDailySlot(quotaInstance))) {
      await requeueProcessing(processingKey, sourceKey, raw, job);
      return;
    }
    try {
      const providerMessageId = await sendText(job.phone, text, job.evolutionInstance);
      await scheduleNextSend(quotaInstance);
      if (isStep2) {
        await emit('STEP2_SENT', { messageId: job.messageId, phone: normalizePhone(job.phone), providerMessageId, step2Text: job.step2Text });
        await incrementMetric('STEP2_SENT');
        await redis.lrem(processingKey, 1, raw);
        return;
      }
      await redis.multi()
        .hset(stateKey, 'sentToday', String(current.sentToday + 1), 'nextColdAt', String(nextColdAt(config)))
        .set(`${conversationPrefix}${normalizePhone(job.phone)}`, JSON.stringify(job), 'EX', 60 * 60 * 24 * 14)
        .exec();
      await emit('STEP1_SENT', { messageId: job.messageId, phone: normalizePhone(job.phone), providerMessageId, step1Text: text });
      await incrementMetric('STEP1_SENT');
      await redis.lrem(processingKey, 1, raw);
    } catch (error) {
      // A vaga somente Ã© consumida quando a Evolution confirma o envio.
      await releaseDailySlot(quotaInstance);
      if (error.rateLimited) {
        await requeueProcessing(processingKey, sourceKey, raw, job);
        await redis.hset(stateKey, 'restrictionDetected', 'true', 'paused', 'true', 'pausedReason', 'RESTRICTION');
        await emit('THROTTLED', { messageId: job.messageId, reason: error.message });
        await incrementMetric('THROTTLED');
      } else if (error.connectionUnavailable) {
        await requeueProcessing(processingKey, sourceKey, raw, job);
        await redis.hset(stateKey, 'paused', 'true', 'pausedReason', 'CONNECTION');
        await emit('RETRYING', { messageId: job.messageId, reason: 'WhatsApp temporariamente indisponível; retomada automática aguardando conexão.' });
      } else if (error.invalidRecipient) {
        await redis.lrem(processingKey, 1, raw);
        await emit('SKIPPED', { messageId: job.messageId, reason: 'Este contato não possui WhatsApp ativo.' });
      } else {
        const attempts = Number(job.attempts || 0) + 1;
        if (attempts < 3) {
          await requeueProcessing(processingKey, sourceKey, raw, { ...job, attempts });
          await emit('RETRYING', { messageId: job.messageId, reason: error.message, attempt: attempts });
        } else {
          await redis.multi().lrem(processingKey, 1, raw).rpush(deadLetterKey, JSON.stringify({ ...job, attempts, failedAt: new Date().toISOString(), error: error.message })).exec();
          await emit('FAILED', { messageId: job.messageId, reason: `${error.message} (após ${attempts} tentativas)` });
          await incrementMetric('FAILED');
        }
      }
    }
  } catch (error) {
    console.error('queue processing failed', error);
  } finally {
    processing = false;
  }
}

async function takeEligible(sourceKey, processingKey) {
  const size = await redis.llen(sourceKey);
  for (let index = 0; index < size; index += 1) {
    const raw = await redis.lmove(sourceKey, processingKey, 'LEFT', 'RIGHT');
    if (!raw) return null;
    try {
      const job = JSON.parse(raw);
      if (await redis.sismember(cancelledCampaignsKey, String(job.campaignId || ''))) {
        await redis.lrem(processingKey, 1, raw);
        continue;
      }
      if ((await dailyQuota(job.evolutionInstance)).exhausted) {
        await requeueProcessing(processingKey, sourceKey, raw, job);
        continue;
      }
      if (!(await sendWindow(job.evolutionInstance)).allowedNow) {
        await requeueProcessing(processingKey, sourceKey, raw, job);
        continue;
      }
      if (!(await redis.sismember(pausedCampaignsKey, String(job.campaignId || '')))) return raw;
    } catch {
      await redis.lrem(processingKey, 1, raw);
      continue;
    }
    await redis.multi().lrem(processingKey, 1, raw).rpush(sourceKey, raw).exec();
  }
  return null;
}

function parkedCampaignKey(campaignId) {
  return `${parkedCampaignPrefix}${campaignId}`;
}

async function parkJob(campaignId, sourceKey, processingKey, raw) {
  const parked = JSON.stringify({ source: sourceKey === priorityQueueKey ? 'priority' : 'cold', raw });
  const removed = await redis.lrem(processingKey, 1, raw);
  if (removed) await redis.rpush(parkedCampaignKey(campaignId), parked);
}

async function extractCampaignJobs(campaignId) {
  let parked = 0;
  const targetKey = parkedCampaignKey(campaignId);
  for (const [sourceKey, kind] of [
    [priorityQueueKey, 'priority'], [queueKey, 'cold'],
    [processingPriorityKey, 'priority'], [processingColdKey, 'cold']
  ]) {
    const jobs = await redis.lrange(sourceKey, 0, -1);
    for (const raw of jobs) {
      try {
        const job = JSON.parse(raw);
        if (String(job.campaignId || '') !== String(campaignId)) continue;
        const removed = await redis.lrem(sourceKey, 1, raw);
        if (removed) {
          await redis.rpush(targetKey, JSON.stringify({ source: kind, raw }));
          parked += 1;
        }
      } catch {
        // O consumidor tratará itens inválidos que não pertencem à campanha.
      }
    }
  }
  return parked;
}

async function restoreCampaignJobs(campaignId) {
  const targetKey = parkedCampaignKey(campaignId);
  let restored = 0;
  for (;;) {
    const parked = await redis.lpop(targetKey);
    if (!parked) break;
    try {
      const item = JSON.parse(parked);
      const destination = item.source === 'priority' ? priorityQueueKey : queueKey;
      await redis.rpush(destination, item.raw);
      restored += 1;
    } catch {
      // Entrada inválida é descartada para não bloquear a retomada.
    }
  }
  await redis.del(targetKey);
  return restored;
}

async function discardCampaignJobs(campaignId) {
  const parked = await extractCampaignJobs(campaignId);
  const alreadyParked = await redis.llen(parkedCampaignKey(campaignId));
  await redis.del(parkedCampaignKey(campaignId));
  return Math.max(parked, Number(alreadyParked || 0));
}

async function requeueProcessing(processingKey, sourceKey, raw, job) {
  await redis.multi().lrem(processingKey, 1, raw).rpush(sourceKey, JSON.stringify(job)).exec();
}

async function recoverProcessing() {
  for (const [processingKey, sourceKey] of [[processingPriorityKey, priorityQueueKey], [processingColdKey, queueKey]]) {
    while (await redis.llen(processingKey)) {
      const raw = await redis.lpop(processingKey);
      if (raw) await redis.lpush(sourceKey, raw);
    }
  }
}

async function recoverConnectionPause() {
  if (!config.deliveryEnabled) return;
  const saved = await redis.hgetall(stateKey);
  if (!['CONNECTION', 'WEBHOOK'].includes(saved.pausedReason)) return;
  const raw = await redis.lindex(priorityQueueKey, 0) || await redis.lindex(queueKey, 0);
  if (!raw) return;
  try {
    const job = JSON.parse(raw);
    const instance = String(job.evolutionInstance || evolution.instance || '').trim();
    if (!instance || !evolution.baseUrl || !evolution.apiKey) return;
    const response = await fetch(`${evolution.baseUrl}/instance/connectionState/${encodeURIComponent(instance)}`, {
      headers: { accept: 'application/json', apikey: evolution.apiKey }
    });
    const body = await response.json().catch(() => ({}));
    const connectionState = String(body?.instance?.state || body?.state || '').toLowerCase();
    if (response.ok && ['open', 'connected'].includes(connectionState)) {
      if (!(await ensureReplyWebhook(instance))) {
        await redis.hset(stateKey, 'paused', 'true', 'pausedReason', 'WEBHOOK');
        return;
      }
      await redis.hset(stateKey, 'paused', 'false', 'restrictionDetected', 'false');
      await redis.hdel(stateKey, 'pausedReason');
      console.log(`delivery resumed automatically for ${instance}`);
    }
  } catch (error) {
    console.error('automatic connection recovery check failed', error.message);
  }
}

async function ensureReplyWebhook(instance) {
  if (!instance || !evolution.baseUrl || !evolution.apiKey || !webhookSecret || !publicBaseUrl) return false;
  try {
    const response = await fetch(`${evolution.baseUrl}/webhook/set/${encodeURIComponent(instance)}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', accept: 'application/json', apikey: evolution.apiKey },
      body: JSON.stringify({ webhook: {
        enabled: true,
        url: `${publicBaseUrl}/webhooks/outreach-bot`,
        events: ['MESSAGES_UPSERT'],
        headers: { 'X-Webhook-Secret': webhookSecret },
        base64: false
      } })
    });
    if (!response.ok) console.error(`reply webhook configuration failed for ${instance}: HTTP ${response.status}`);
    return response.ok;
  } catch (error) {
    console.error('reply webhook configuration failed', error.message);
    return false;
  }
}

async function ensureQueueWebhook() {
  const raw = await redis.lindex(priorityQueueKey, 0) || await redis.lindex(queueKey, 0);
  if (!raw) return;
  try {
    const job = JSON.parse(raw);
    const instance = String(job.evolutionInstance || evolution.instance || '').trim();
    await ensureReplyWebhook(instance);
  } catch (error) {
    console.error('could not inspect queue webhook', error.message);
  }
}

app.get('/health', async (_req, res) => {
  try { await redis.ping(); res.json({ status: 'ok' }); } catch { res.status(503).json({ status: 'redis-unavailable' }); }
});
app.get('/v1/status', authorize, async (req, res) => res.json(await state(req.query.instance)));
app.get('/v1/quota', authorize, async (req, res) => res.json(await dailyQuota(req.query.instance)));
app.post('/v1/pause', authorize, async (_req, res) => { await redis.hset(stateKey, 'paused', 'true', 'pausedReason', 'MANUAL'); res.json(await state()); });
app.post('/v1/resume', authorize, async (_req, res) => { await redis.hset(stateKey, 'paused', 'false'); await redis.hdel(stateKey, 'pausedReason'); res.json(await state()); });
app.post('/v1/campaigns/:id/pause', authorize, async (req, res) => {
  await redis.sadd(pausedCampaignsKey, req.params.id);
  const parked = await extractCampaignJobs(req.params.id);
  res.json({ campaignId: req.params.id, paused: true, parked });
});
app.post('/v1/campaigns/:id/resume', authorize, async (req, res) => {
  if (await redis.sismember(cancelledCampaignsKey, req.params.id)) return res.status(409).json({ error: 'campaign-cancelled' });
  const restored = await restoreCampaignJobs(req.params.id);
  await redis.srem(pausedCampaignsKey, req.params.id);
  res.json({ campaignId: req.params.id, paused: false, restored });
});
app.post('/v1/campaigns/:id/cancel', authorize, async (req, res) => {
  await redis.sadd(cancelledCampaignsKey, req.params.id);
  await redis.srem(pausedCampaignsKey, req.params.id);
  const removed = await discardCampaignJobs(req.params.id);
  res.json({ campaignId: req.params.id, cancelled: true, removed });
});
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
await recoverProcessing();
setTimeout(ensureQueueWebhook, 2_000).unref();
setInterval(processOne, 1_000).unref();
setInterval(recoverConnectionPause, 15_000).unref();
setInterval(ensureQueueWebhook, 10 * 60_000).unref();
setInterval(flushEvents, 15_000).unref();
setInterval(sendReport, 15 * 60 * 1_000).unref();
app.listen(port, () => console.log(`outreach-bot listening on ${port}, paused=${config.paused}`));
