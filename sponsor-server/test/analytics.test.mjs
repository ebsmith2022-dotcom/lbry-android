import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import sharp from 'sharp';
import { createApp, passwordHash } from '../server.mjs';
import { localDay, reportRange } from '../analytics.mjs';
let server, close, dir, base, cookie, csrf, campaign;
let now = Date.parse('2026-11-02T17:00:00Z');
async function admin(path, body) {
  const res = await fetch(base + path, { method: body === undefined ? 'GET' : 'POST', headers: { cookie: cookie || '', 'x-csrf-token': csrf || '', Origin: base, 'X-Dashboard': '1', 'Content-Type': 'application/json' }, body: body === undefined ? undefined : JSON.stringify(body) });
  return res;
}
async function delivery() { return (await fetch(base + '/v1/banner')).json(); }
async function event(token, type, extra = {}) { return fetch(base + '/v1/events', { method: 'POST', headers: { 'Content-Type': 'application/json', ...extra }, body: JSON.stringify({ token, type }) }); }
async function report(period = 'daily', date = '2026-11-02') { return (await admin(`/api/analytics?period=${period}&date=${date}`)).json(); }
before(async () => {
  dir = mkdtempSync(join(tmpdir(), 'lbry-metrics-test-'));
  const instance = createApp({ dataDir: dir, adminHash: passwordHash('test-only'), clock: () => now }); close = instance.close;
  server = await new Promise((resolve, reject) => { const s = instance.app.listen(0, '127.0.0.1', error => error ? reject(error) : resolve(s)); });
  base = `http://127.0.0.1:${server.address().port}`;
  const login = await admin('/api/login', { password: 'test-only' }); cookie = login.headers.get('set-cookie').split(';')[0]; csrf = (await login.json()).csrf;
  const image = (await sharp({ create: { width: 960, height: 150, channels: 3, background: '#174e3e' } }).png().toBuffer()).toString('base64');
  const created = await admin('/api/campaigns', { sponsor: 'Metrics test', destination: 'https://example.com', amount: '0', starts: now - 1000, ends: now + 90 * 86400_000, image });
  campaign = (await created.json()).id;
  await admin(`/api/campaigns/${campaign}/complimentary`, { reason: 'Synthetic analytics test' });
  await admin(`/api/campaigns/${campaign}/approve`, {});
});
after(async () => { await new Promise(resolve => server.close(resolve)); close(); rmSync(dir, { recursive: true, force: true }); });
test('reports require owner login; fetching ads and previews does not count an impression', async () => {
  assert.equal((await fetch(base + '/api/analytics')).status, 401);
  const ad = await delivery(); await fetch(base + ad.image);
  assert.equal((await report()).campaigns[0].impressions, 0);
});
test('receipts reject forged, expired, cross-origin and malformed events', async () => {
  assert.equal((await event('a'.repeat(64), 'impression')).status, 400);
  const ad = await delivery();
  assert.equal((await event(ad.event_token, 'other')).status, 400);
  assert.equal((await event(ad.event_token, 'impression', { Origin: 'https://evil.example' })).status, 403);
  now += 5 * 60_000 + 1;
  assert.equal((await event(ad.event_token, 'impression')).status, 400);
});
test('impressions and clicks deduplicate per receipt; a fast click establishes its impression', async () => {
  const ad = await delivery();
  for (const type of ['impression', 'impression', 'click', 'click']) assert.equal((await event(ad.event_token, type)).status, 204);
  let row = (await report()).campaigns[0]; assert.equal(row.impressions, 1); assert.equal(row.clicks, 1);
  const fast = await delivery(); await event(fast.event_token, 'click'); await event(fast.event_token, 'impression');
  row = (await report()).campaigns[0]; assert.equal(row.impressions, 2); assert.equal(row.clicks, 2);
});
test('daily, Monday-based weekly and calendar monthly totals agree across days', async () => {
  now = Date.parse('2026-11-03T17:00:00Z');
  // Refresh expired owner session; event timestamps always use server time.
  const login = await admin('/api/login', { password: 'test-only' }); cookie = login.headers.get('set-cookie').split(';')[0]; csrf = (await login.json()).csrf;
  const ad = await delivery(); await event(ad.event_token, 'impression');
  assert.equal((await report('daily', '2026-11-02')).campaigns[0].impressions, 2);
  assert.equal((await report('daily', '2026-11-03')).campaigns[0].impressions, 1);
  const week = await report('weekly', '2026-11-03'); assert.equal(week.start, '2026-11-02'); assert.equal(week.end, '2026-11-09'); assert.equal(week.campaigns[0].impressions, 3);
  assert.equal((await report('monthly', '2026-11-22')).campaigns[0].impressions, 3);
  assert.equal((await report('monthly', '2026-10-22')).campaigns[0].impressions, 0);
  assert.equal((await admin('/api/analytics?period=daily&date=2026-02-30')).status, 400);
  assert.equal((await admin('/api/analytics?period=forever')).status, 400);
});
test('Eastern calendar handles DST, midnight, month and year boundaries', () => {
  assert.equal(localDay(Date.parse('2026-11-01T04:30:00Z')), '2026-11-01');
  assert.equal(localDay(Date.parse('2026-11-01T06:30:00Z')), '2026-11-01');
  assert.equal(localDay(Date.parse('2026-11-02T04:30:00Z')), '2026-11-01');
  assert.equal(localDay(Date.parse('2026-11-02T05:00:00Z')), '2026-11-02');
  assert.deepEqual(reportRange('monthly', '2026-12-31'), { start: '2026-12-01', end: '2027-01-01' });
  assert.deepEqual(reportRange('weekly', '2027-01-01'), { start: '2026-12-28', end: '2027-01-04' });
});
