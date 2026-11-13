import { createHash } from 'node:crypto';
export function installLikes(app, db) {
 db.exec('CREATE TABLE IF NOT EXISTS video_likes (claim TEXT NOT NULL, voter TEXT NOT NULL, PRIMARY KEY(claim,voter)) WITHOUT ROWID;');
 const validClaim = value => /^[a-f0-9]{40}$/.test(value || '');
 const validToken = value => /^[a-f0-9]{64}$/.test(value || '');
 const hash = value => createHash('sha256').update(value).digest('hex');
 const state = (claim,token) => ({ count:db.prepare('SELECT COUNT(*) AS n FROM video_likes WHERE claim=?').get(claim).n, liked:!!(token && db.prepare('SELECT 1 FROM video_likes WHERE claim=? AND voter=?').get(claim,hash(token))) });
 app.get('/v1/likes/:claim', (req,res)=>{
  const token=req.get('x-like-token');
  if(!validClaim(req.params.claim) || (token && !validToken(token))) return res.status(400).json({error:'Invalid video or like token.'});
  res.json(state(req.params.claim,token));
 });
 app.post('/v1/likes/:claim', (req,res)=>{
  const token=req.get('x-like-token');
  if(req.get('origin')) return res.status(403).json({error:'Use the LBRY Community app.'});
  if(!validClaim(req.params.claim) || !validToken(token) || typeof req.body?.liked !== 'boolean') return res.status(400).json({error:'Invalid like request.'});
  if(req.body.liked) db.prepare('INSERT OR IGNORE INTO video_likes(claim,voter) VALUES(?,?)').run(req.params.claim,hash(token));
  else db.prepare('DELETE FROM video_likes WHERE claim=? AND voter=?').run(req.params.claim,hash(token));
  res.json(state(req.params.claim,token));
 });
}
