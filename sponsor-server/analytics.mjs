import { randomBytes, createHash } from 'node:crypto';
export const REPORT_TIMEZONE = 'America/Detroit';
const hash = value => createHash('sha256').update(value).digest('hex');
export function localDay(time) {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: REPORT_TIMEZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date(time));
  const part = type => parts.find(p => p.type === type).value;
  return `${part('year')}-${part('month')}-${part('day')}`;
}
export function reportRange(period, day) {
  if (!['daily', 'weekly', 'monthly'].includes(period) || !/^\d{4}-\d{2}-\d{2}$/.test(day)) throw Error('Invalid report period or date.');
  const date = new Date(day + 'T00:00:00Z');
  if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== day) throw Error('Invalid report date.');
  if (period === 'weekly') date.setUTCDate(date.getUTCDate() - (date.getUTCDay() + 6) % 7);
  if (period === 'monthly') date.setUTCDate(1);
  const start = date.toISOString().slice(0, 10);
  if (period === 'monthly') date.setUTCMonth(date.getUTCMonth() + 1);
  else date.setUTCDate(date.getUTCDate() + (period === 'weekly' ? 7 : 1));
  return { start, end: date.toISOString().slice(0, 10) };
}
export function analytics(db, clock) {
  db.exec(`CREATE TABLE IF NOT EXISTS ad_receipts (
    token TEXT PRIMARY KEY, campaign TEXT NOT NULL, expires INTEGER NOT NULL,
    impression INTEGER NOT NULL DEFAULT 0, click INTEGER NOT NULL DEFAULT 0);
    CREATE INDEX IF NOT EXISTS receipt_expiry ON ad_receipts(expires);
    CREATE TABLE IF NOT EXISTS ad_daily (
    campaign TEXT NOT NULL, day TEXT NOT NULL, impressions INTEGER NOT NULL DEFAULT 0,
    clicks INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(campaign, day));`);
  let lastPurge = 0;
  function purge() {
    if (clock() - lastPurge < 60_000) return;
    db.prepare('DELETE FROM ad_receipts WHERE expires<=?').run(clock());
    lastPurge = clock();
  }
  return {
    issue(campaign) {
      purge();
      // Bound temporary accounting storage. Banner delivery remains available if full.
      if (db.prepare('SELECT count(*) AS n FROM ad_receipts').get().n >= 100_000) return null;
      const token = randomBytes(32).toString('hex');
      db.prepare('INSERT INTO ad_receipts(token,campaign,expires) VALUES(?,?,?)').run(hash(token), campaign, clock() + 5 * 60_000);
      return token;
    },
    record(token, type) {
      if (typeof token !== 'string' || !/^[a-f0-9]{64}$/.test(token) || !['impression', 'click'].includes(type)) return false;
      purge();
      const receipt = db.prepare('SELECT * FROM ad_receipts WHERE token=? AND expires>?').get(hash(token), clock());
      if (!receipt) return false;
      const impression = receipt.impression ? 0 : 1;
      const click = type === 'click' && !receipt.click ? 1 : 0;
      if (!impression && !click) return true;
      // A confirmed click also establishes the impression if it happened before the dwell timer.
      db.exec('BEGIN IMMEDIATE');
      try {
        db.prepare('INSERT INTO ad_daily(campaign,day,impressions,clicks) VALUES(?,?,?,?) ON CONFLICT(campaign,day) DO UPDATE SET impressions=impressions+excluded.impressions,clicks=clicks+excluded.clicks').run(receipt.campaign, localDay(clock()), impression, click);
        db.prepare('UPDATE ad_receipts SET impression=1,click=MAX(click,?) WHERE token=?').run(click, receipt.token);
        db.exec('COMMIT');
      } catch (error) { db.exec('ROLLBACK'); throw error; }
      return true;
    },
    report(period, day) {
      purge();
      const range = reportRange(period, day);
      const campaigns = db.prepare(`SELECT c.id,c.sponsor,COALESCE(SUM(d.impressions),0) AS impressions,COALESCE(SUM(d.clicks),0) AS clicks
        FROM campaigns c LEFT JOIN ad_daily d ON d.campaign=c.id AND d.day>=? AND d.day<?
        GROUP BY c.id ORDER BY impressions DESC,c.sponsor`).all(range.start, range.end);
      return { period, date: day, today: localDay(clock()), timezone: REPORT_TIMEZONE, ...range, campaigns };
    }
  };
}
