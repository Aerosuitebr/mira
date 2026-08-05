export function normalizePhone(value) {
  return String(value || '').replace(/@.+$/, '').replace(/\D/g, '');
}

export function reportDay(now = new Date(), timeZone = 'America/Sao_Paulo') {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(now);
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  return `${values.year}-${values.month}-${values.day}`;
}

export function selectStep1Text(job, random = Math.random) {
  const company = String(job.companyName || '').trim();
  if (!company) return job.step1Text;
  const variants = [
    `Olá, boa tarde! Tudo bem? Neste contato falo com o responsável comercial da ${company}?`,
    `Boa tarde! Poderia confirmar se este é o melhor contato para falar com o responsável comercial da ${company}?`,
    `Olá! Tudo bem? Posso falar com quem cuida da área comercial da ${company}?`
  ];
  return variants[Math.floor(random() * variants.length)];
}

export function jobStep(job) {
  return job.type === 'STEP2' ? 'STEP2' : 'STEP1';
}

export function canOpenColdConversation(saved, config, now = Date.now()) {
  const sentToday = Number(saved.sentToday || 0);
  return sentToday < config.dailyCap && now >= Number(saved.nextColdAt || 0);
}

export function nextColdAt(config, random = Math.random, now = Date.now()) {
  const seconds = config.minIntervalSeconds
    + random() * (config.maxIntervalSeconds - config.minIntervalSeconds);
  return now + Math.floor(seconds * 1000);
}
