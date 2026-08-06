import test from 'node:test';
import assert from 'node:assert/strict';
import { canOpenColdConversation, jobStep, nextColdAt, normalizePhone, reportDay, selectStep1Text } from '../src/state.js';

test('normalizes Evolution and E.164 phone forms', () => {
  assert.equal(normalizePhone('552199836870@s.whatsapp.net'), '552199836870');
  assert.equal(normalizePhone('+55 (21) 99836-870'), '552199836870');
});

test('uses the configured business timezone for the daily report', () => {
  const instant = new Date('2026-08-06T01:30:00.000Z');
  assert.equal(reportDay(instant, 'America/Sao_Paulo'), '2026-08-05');
  assert.equal(reportDay(instant, 'UTC'), '2026-08-06');
});

test('step 1 preserves the reviewed text when it was configured', () => {
  const text = selectStep1Text({ companyName: 'Aero Suite', step1Text: 'Olá! Falo com o comercial da Aero Suite?' }, () => 0.8);
  assert.match(text, /Aero Suite/);
  assert.doesNotMatch(text, /https?:\/\//i);
});

test('cold openings respect daily cap and cadence, but stage 2 is not a cold opening', () => {
  const config = { dailyCap: 15, minIntervalSeconds: 180, maxIntervalSeconds: 300 };
  assert.equal(canOpenColdConversation({ sentToday: '15' }, config, 1_000), false);
  assert.equal(canOpenColdConversation({ sentToday: '2', nextColdAt: '2000' }, config, 1_000), false);
  assert.equal(canOpenColdConversation({ sentToday: '2', nextColdAt: '1000' }, config, 1_000), true);
  assert.equal(jobStep({ type: 'STEP2' }), 'STEP2');
  assert.equal(nextColdAt(config, () => 0, 1_000), 181_000);
});
