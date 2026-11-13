import { analytics, localDay } from './analytics.mjs';
import express from 'express';
import sharp from 'sharp';
import { DatabaseSync } from 'node:sqlite';
import { randomBytes, randomUUID, scryptSync, timingSafeEqual, createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const digest = value => createHash('sha256').update(value).digest('hex');
export function passwordHash(password) {
  const salt = randomBytes(16).toString('hex');
  return `${salt}:${scryptSync(password, salt, 64).toString('hex')}`;
}
function validPassword(password, hash) {
  if (typeof password !== 'string' || password.length > 512) return false;
  const [salt, encoded] = hash.split(':');
  return timingSafeEqual(scryptSync(password, salt, 64), Buffer.from(encoded, 'hex'));
}
function httpsUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && !url.username && !url.password && url.hostname.includes('.') && value.length <= 2048;
  } catch { return false; }
}
export function createApp({ dataDir, adminHash, clock = Date.now, secure = false }) {
  mkdirSync(dataDir, { recursive: true, mode: 0o700 });
  const db = new DatabaseSync(join(dataDir, 'sponsors.sqlite'));
  db.exec(`PRAGMA journal_mode=WAL;
    CREATE TABLE IF NOT EXISTS campaigns (
      id TEXT PRIMARY KEY, sponsor TEXT NOT NULL, destination TEXT NOT NULL,
      starts INTEGER NOT NULL, ends INTEGER NOT NULL, amount TEXT NOT NULL,
      status TEXT NOT NULL DEFAULT 'pending', txid TEXT UNIQUE, image BLOB NOT NULL, created INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS sessions (token TEXT PRIMARY KEY, csrf TEXT NOT NULL, expires INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS attempts (ip TEXT NOT NULL, created INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS audit (id INTEGER PRIMARY KEY, campaign TEXT, action TEXT NOT NULL, created INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY CHECK(id=1), enabled INTEGER NOT NULL);
    INSERT OR IGNORE INTO settings VALUES(1, 1);`);
  if (!db.prepare('PRAGMA table_info(campaigns)').all().some(c => c.name === 'complimentary_reason')) {
    db.exec('ALTER TABLE campaigns ADD COLUMN complimentary_reason TEXT');
  }
  const metrics = analytics(db, clock);
  const app = express();
  app.disable('x-powered-by');
  app.use((req, res, next) => {
    res.set({ 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'no-referrer', 'X-Frame-Options': 'DENY',
      'Content-Security-Policy': "default-src 'self'; img-src 'self' blob:; script-src 'self'; style-src 'self'; connect-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'" });
    // Reject browser cross-origin mutations, including login. No CORS is enabled.
    if (req.method !== 'GET' && req.method !== 'HEAD' && !(req.path === '/v1/events' && req.method === 'POST')) {
      const expected = `${secure ? 'https' : 'http'}://${req.get('host')}`;
      if (req.get('origin') !== expected || req.get('x-dashboard') !== '1') return res.status(403).json({ error: 'Open the dashboard directly to make changes.' });
    }
    next();
  });
  const dashboardJson = express.json({ limit: '3mb' });
  app.use((req, res, next) => req.path === '/v1/events' ? next() : dashboardJson(req, res, next));
  const sessionFor = req => {
    const cookie = (req.headers.cookie || '').split(';').map(s => s.trim()).find(s => s.startsWith('sponsor_session='));
    if (!cookie) return null;
    return db.prepare('SELECT * FROM sessions WHERE token=? AND expires>?').get(digest(cookie.slice(16)), clock());
  };
  const requireAdmin = (req, res, next) => {
    req.admin = sessionFor(req);
    if (!req.admin) return res.status(401).json({ error: 'Please sign in.' });
    if (req.method !== 'GET' && req.get('x-csrf-token') !== req.admin.csrf) return res.status(403).json({ error: 'Refresh the dashboard and try again.' });
    next();
  };
  const active = row => row && row.status === 'approved' && (row.txid || row.complimentary_reason) && row.starts <= clock() && row.ends > clock() && db.prepare('SELECT enabled FROM settings WHERE id=1').get().enabled;
  const audit = (id, action) => db.prepare('INSERT INTO audit(campaign,action,created) VALUES(?,?,?)').run(id, action, clock());
  app.post('/api/login', (req, res) => {
    const now = clock(), ip = req.ip;
    db.prepare('DELETE FROM attempts WHERE created<?').run(now - 15 * 60_000);
    db.prepare('DELETE FROM sessions WHERE expires<=?').run(now);
    if (db.prepare('SELECT count(*) AS n FROM attempts WHERE ip=?').get(ip).n >= 5) return res.status(429).json({ error: 'Too many attempts. Try again in 15 minutes.' });
    db.prepare('INSERT INTO attempts VALUES(?,?)').run(ip, now);
    if (!validPassword(req.body?.password, adminHash)) return res.status(401).json({ error: 'Incorrect password.' });
    db.prepare('DELETE FROM attempts WHERE ip=?').run(ip);
    const token = randomBytes(32).toString('hex'), csrf = randomBytes(32).toString('hex');
    db.prepare('INSERT INTO sessions VALUES(?,?,?)').run(digest(token), csrf, now + 8 * 60 * 60_000);
    res.cookie('sponsor_session', token, { httpOnly: true, secure, sameSite: 'strict', maxAge: 8 * 60 * 60_000, path: '/' });
    res.json({ csrf });
  });
  app.get('/api/session', requireAdmin, (req, res) => res.json({ csrf: req.admin.csrf }));
  app.post('/api/logout', requireAdmin, (req, res) => {
    db.prepare('DELETE FROM sessions WHERE token=?').run(req.admin.token);
    res.clearCookie('sponsor_session', { path: '/' }).json({ ok: true });
  });
  app.get('/api/analytics', requireAdmin, (req, res) => {
    try { res.json(metrics.report(req.query.period || 'daily', req.query.date || localDay(clock()))); }
    catch { res.status(400).json({ error: 'Choose daily, weekly or monthly and a valid YYYY-MM-DD date.' }); }
  });
  app.post('/v1/events', express.json({ limit: '1kb' }), (req, res) => {
    // Only short-lived delivery receipts can submit events. Never accepts a campaign ID or client date.
    if (req.get('origin')) return res.status(403).end();
    if (!metrics.record(req.body?.token, req.body?.type)) return res.status(400).end();
    res.status(204).end();
  });
  app.get('/api/campaigns', requireAdmin, (req, res) => {
    const rows = db.prepare('SELECT id,sponsor,destination,starts,ends,amount,status,txid,complimentary_reason,created FROM campaigns ORDER BY created DESC').all();
    res.json({ campaigns: rows.map(row => ({ ...row, live: !!active(row) })), enabled: !!db.prepare('SELECT enabled FROM settings WHERE id=1').get().enabled,
      audit: db.prepare('SELECT campaign,action,created FROM audit ORDER BY id DESC LIMIT 50').all() });
  });
  app.post('/api/campaigns', requireAdmin, async (req, res) => {
    const { sponsor, destination, starts, ends, amount, image } = req.body || {};
    if (typeof sponsor !== 'string' || !sponsor.trim() || sponsor.length > 80 || !httpsUrl(destination) ||
        !Number.isSafeInteger(starts) || !Number.isSafeInteger(ends) || ends <= starts || ends <= clock() ||
        typeof amount !== 'string' || !/^\d{1,10}(\.\d{1,8})?$/.test(amount) || Number(amount) < 0 ||
        typeof image !== 'string' || !/^[A-Za-z0-9+/]+={0,2}$/.test(image)) return res.status(400).json({ error: 'Check the name, HTTPS link, dates, non-negative LBC amount and image.' });
    try {
      const raw = Buffer.from(image, 'base64');
      if (raw.length > 2 * 1024 * 1024) throw Error('large');
      const processor = sharp(raw, { limitInputPixels: 4_000_000, animated: false });
      const info = await processor.metadata();
      if (!['png', 'jpeg'].includes(info.format) || (info.pages || 1) > 1 || info.width < 320 || info.height < 50 || info.width / info.height < 3 || info.width / info.height > 8) throw Error('shape');
      // Decode and re-encode: no SVG, executable content, animation or original metadata.
      const clean = await processor.resize({ width: 1280, withoutEnlargement: true }).flatten({ background: '#ffffff' }).png().toBuffer();
      const id = randomUUID();
      db.prepare('INSERT INTO campaigns(id,sponsor,destination,starts,ends,amount,image,created) VALUES(?,?,?,?,?,?,?,?)').run(id, sponsor.trim(), destination, starts, ends, amount, clean, clock());
      audit(id, 'Created for review');
      res.status(201).json({ id });
    } catch { res.status(400).json({ error: 'Use a static PNG/JPEG under 2 MB, at least 320 × 50, with width 3–8 times its height.' }); }
  });
  app.post('/api/campaigns/:id/:action', requireAdmin, (req, res) => {
    const row = db.prepare('SELECT * FROM campaigns WHERE id=?').get(req.params.id);
    if (!row) return res.status(404).json({ error: 'Campaign not found.' });
    const action = req.params.action;
    if (action === 'payment') {
      const txid = req.body?.txid;
      if (row.status !== 'pending' || row.txid || row.complimentary_reason || Number(row.amount) <= 0 || !/^[a-fA-F0-9]{64}$/.test(txid || '') || req.body?.confirmed !== true) return res.status(400).json({ error: 'Confirm the amount and receiving address in your wallet, then enter the 64-character transaction ID. Payment can only be recorded once, while pending.' });
      try { db.prepare('UPDATE campaigns SET txid=? WHERE id=?').run(txid.toLowerCase(), row.id); }
      catch { return res.status(409).json({ error: 'This transaction is already assigned to another campaign.' }); }
      audit(row.id, 'Payment manually confirmed by owner');
    } else if (action === 'complimentary') {
      const reason = typeof req.body?.reason === 'string' ? req.body.reason.trim() : '';
      if (row.status !== 'pending' || row.txid || row.complimentary_reason || row.ends <= clock() || reason.length < 5 || reason.length > 300) {
        return res.status(400).json({ error: 'Use an unpaid pending campaign and enter a reason of 5–300 characters.' });
      }
      db.prepare('UPDATE campaigns SET complimentary_reason=? WHERE id=?').run(reason, row.id);
      audit(row.id, 'Owner marked complimentary / test: ' + reason);
    } else if (action === 'approve') {
      if (!(row.txid || row.complimentary_reason) || !['pending', 'paused'].includes(row.status) || row.ends <= clock()) return res.status(409).json({ error: 'Approval needs confirmed payment or an owner-recorded complimentary reason, a pending/paused campaign and a future end date.' });
      db.prepare("UPDATE campaigns SET status='approved' WHERE id=?").run(row.id); audit(row.id, 'Approved');
    } else if (action === 'pause' && row.status === 'approved') {
      db.prepare("UPDATE campaigns SET status='paused' WHERE id=?").run(row.id); audit(row.id, 'Paused');
    } else if (action === 'reject' && ['pending', 'paused', 'approved'].includes(row.status)) {
      db.prepare("UPDATE campaigns SET status='rejected' WHERE id=?").run(row.id); audit(row.id, 'Rejected / removed');
    } else return res.status(409).json({ error: 'This action is not available for the current campaign.' });
    res.json({ ok: true });
  });
  app.post('/api/switch', requireAdmin, (req, res) => {
    if (typeof req.body?.enabled !== 'boolean') return res.status(400).json({ error: 'Choose on or off.' });
    db.prepare('UPDATE settings SET enabled=? WHERE id=1').run(Number(req.body.enabled));
    audit(null, req.body.enabled ? 'All ads enabled' : 'All ads stopped'); res.json({ ok: true });
  });
  app.get('/v1/banner', (req, res) => {
    const rows = db.prepare("SELECT id,sponsor,destination,starts,ends,status,txid,complimentary_reason FROM campaigns WHERE status='approved' AND starts<=? AND ends>? ORDER BY created").all(clock(), clock()).filter(active);
    if (!rows.length) return res.status(204).end();
    const row = rows[Math.floor(Math.random() * rows.length)];
    // No private payment records, wallet identity or viewer identifiers in this API.
    res.json({ id: row.id, sponsor: row.sponsor, destination: row.destination, image: `/v1/images/${row.id}`, event_token: metrics.issue(row.id), ttl_seconds: Math.max(1, Math.min(60, Math.floor((row.ends - clock()) / 1000))) });
  });
  app.get('/v1/images/:id', (req, res) => {
    const row = db.prepare('SELECT * FROM campaigns WHERE id=?').get(req.params.id);
    if (!row || (!sessionFor(req) && !active(row))) return res.status(404).end();
    res.type('png').send(Buffer.from(row.image));
  });
  app.use(express.static(join(here, 'public'), { etag: false, maxAge: 0 }));
  app.use((error, req, res, next) => {
    res.status(error.status === 413 ? 413 : 400).json({ error: error.status === 413 ? 'Upload is too large.' : 'Unable to process the request.' });
  });
  return { app, close: () => db.close() };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  process.umask(0o077);
  const dataDir = resolve(process.env.SPONSOR_DATA || join(here, '.local-data'));
  mkdirSync(dataDir, { recursive: true, mode: 0o700 });
  const credentials = join(dataDir, 'admin.json');
  if (!existsSync(credentials)) {
    const password = randomBytes(18).toString('base64url');
    writeFileSync(credentials, JSON.stringify({ hash: passwordHash(password) }), { mode: 0o600 });
    writeFileSync(join(dataDir, 'first-login.txt'), `Local dashboard password: ${password}\nKeep this private. This is not a wallet password.\n`, { mode: 0o600 });
    console.log(`First-login password saved privately in ${join(dataDir, 'first-login.txt')}`);
  }
  const { hash } = JSON.parse(readFileSync(credentials, 'utf8'));
  const { app } = createApp({ dataDir, adminHash: hash });
  const port = Number(process.env.PORT || 8787);
  app.listen(port, '127.0.0.1', () => console.log(`Local sponsor dashboard: http://127.0.0.1:${port}`));
}
