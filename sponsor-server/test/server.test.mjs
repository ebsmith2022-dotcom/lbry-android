import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import sharp from 'sharp';
import { createApp, passwordHash } from '../server.mjs';
let server, close, dir, base, cookie, csrf, image;
let now = Date.now();
const password = 'Only-a-test-password-not-a-real-account';
async function request(path, body, auth = true, extra = {}) {
  const headers = { ...(auth && cookie ? { cookie, 'x-csrf-token': csrf } : {}), ...extra };
  if (body !== undefined) Object.assign(headers, { 'Content-Type': 'application/json', Origin: base, 'X-Dashboard': '1' }, extra);
  return fetch(base + path, { method: body === undefined ? 'GET' : 'POST', headers, body: body === undefined ? undefined : JSON.stringify(body) });
}
async function create(overrides = {}) {
  return request('/api/campaigns', { sponsor: 'Test sponsor', destination: 'https://example.com/sponsor', starts: now - 1000, ends: now + 3600_000, amount: '100.5', image, ...overrides });
}
async function paid(id, tx = 'a'.repeat(64)) { return request(`/api/campaigns/${id}/payment`, { txid: tx, confirmed: true }); }
before(async () => {
  dir = mkdtempSync(join(tmpdir(), 'lbry-sponsors-test-'));
  const instance = createApp({ dataDir: dir, adminHash: passwordHash(password), clock: () => now }); close = instance.close;
  server = await new Promise(resolve => { const s = instance.app.listen(0, '127.0.0.1', () => resolve(s)); });
  base = `http://127.0.0.1:${server.address().port}`;
  image = (await sharp({ create: { width: 960, height: 150, channels: 3, background: '#174e3e' } }).png().toBuffer()).toString('base64');
});
after(async () => { await new Promise(resolve => server.close(resolve)); close(); rmSync(dir, { recursive: true, force: true }); });
test('admin is private; login rejects other origins and protects its cookie', async () => {
  assert.equal((await request('/api/campaigns', undefined, false)).status, 401);
  assert.equal((await request('/api/login', { password }, false, { Origin: 'https://evil.example' })).status, 403);
  const login = await request('/api/login', { password }, false); assert.equal(login.status, 200);
  assert.match(login.headers.get('set-cookie'), /HttpOnly/); assert.match(login.headers.get('set-cookie'), /SameSite=Strict/);
  cookie = login.headers.get('set-cookie').split(';')[0]; csrf = (await login.json()).csrf;
});
test('mutations require CSRF and public viewers cannot create campaigns', async () => {
  assert.equal((await request('/api/switch', { enabled: false }, true, { 'x-csrf-token': 'wrong' })).status, 403);
  assert.equal((await request('/api/campaigns', {}, false)).status, 401);
});
test('pending and paid-but-unapproved campaigns are invisible; pause/reject revoke serving', async () => {
  const response = await create(); assert.equal(response.status, 201); const { id } = await response.json();
  assert.equal((await request('/v1/banner', undefined, false)).status, 204);
  assert.equal((await request(`/v1/images/${id}`, undefined, false)).status, 404);
  assert.equal((await request(`/api/campaigns/${id}/approve`, {})).status, 409);
  assert.equal((await paid(id)).status, 200);
  assert.equal((await request('/v1/banner', undefined, false)).status, 204);
  assert.equal((await request(`/api/campaigns/${id}/approve`, {})).status, 200);
  const ad = await (await request('/v1/banner', undefined, false)).json();
  assert.equal(ad.id, id); assert.equal(ad.txid, undefined); assert.equal(ad.amount, undefined); assert.equal(ad.ttl_seconds, 60);
  const banner = await request(ad.image, undefined, false); assert.equal(banner.status, 200); assert.equal(banner.headers.get('cache-control'), 'no-store');
  assert.equal((await sharp(Buffer.from(await banner.arrayBuffer())).metadata()).format, 'png');
  await request(`/api/campaigns/${id}/pause`, {});
  assert.equal((await request('/v1/banner', undefined, false)).status, 204);
  assert.equal((await request(ad.image, undefined, false)).status, 404);
  await request(`/api/campaigns/${id}/reject`, {});
  assert.equal((await request(`/api/campaigns/${id}/approve`, {})).status, 409);
});
test('duplicate transactions cannot pay for another campaign', async () => {
  const { id } = await (await create()).json(); assert.equal((await paid(id)).status, 409);
});
test('future campaigns wait, expire automatically, and global stop overrides approval', async () => {
  const { id } = await (await create({ starts: now + 1000, ends: now + 3000 })).json();
  await paid(id, 'b'.repeat(64)); await request(`/api/campaigns/${id}/approve`, {});
  assert.equal((await request('/v1/banner', undefined, false)).status, 204);
  now += 1100; assert.equal((await request('/v1/banner', undefined, false)).status, 200);
  await request('/api/switch', { enabled: false }); assert.equal((await request('/v1/banner', undefined, false)).status, 204);
  await request('/api/switch', { enabled: true }); now += 2000;
  assert.equal((await request('/v1/banner', undefined, false)).status, 204);
  assert.equal((await request(`/v1/images/${id}`, undefined, false)).status, 404);
});
test('invalid links, amounts, time ranges and images are rejected', async () => {
  for (const patch of [{ destination: 'javascript:alert(1)' }, { destination: 'https://user:pass@example.com' }, { amount: '-1' }, { amount: 'NaN' }, { ends: now - 1 }, { image: Buffer.from('<svg></svg>').toString('base64') }]) assert.equal((await create(patch)).status, 400);
  const square = (await sharp({ create: { width: 100, height: 100, channels: 3, background: '#fff' } }).png().toBuffer()).toString('base64');
  assert.equal((await create({ image: square })).status, 400);
});
test('logout revokes old cookie and repeated failed login is throttled', async () => {
  assert.equal((await request('/api/logout', {})).status, 200);
  assert.equal((await request('/api/campaigns')).status, 401);
  for (let i = 0; i < 5; i++) assert.equal((await request('/api/login', { password: 'wrong' }, false)).status, 401);
  assert.equal((await request('/api/login', { password }, false)).status, 429);
});
