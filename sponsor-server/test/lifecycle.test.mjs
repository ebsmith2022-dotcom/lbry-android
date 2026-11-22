import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import sharp from 'sharp';
import { createApp, passwordHash } from '../server.mjs';
let dir, instance, server, base, cookie, csrf, image, now = Date.now();
const password = 'Local-lifecycle-fixture-only';
async function request(path, body, auth = true, token = csrf) {
  return fetch(base + path, {method: body === undefined ? 'GET' : 'POST', headers: {
    ...(auth ? { cookie, 'x-csrf-token': token } : {}),
    ...(body === undefined ? {} : {'Content-Type':'application/json', Origin:base, 'X-Dashboard':'1'})
  },body:body === undefined ? undefined : JSON.stringify(body)});
}
async function create() {
  const r = await request('/api/campaigns', {sponsor:'Lifecycle fixture', destination:'https://example.com', starts:now-1000, ends:now+10000, amount:'100', image});
  assert.equal(r.status,201); return (await r.json()).id;
}
before(async () => {
  dir = mkdtempSync(join(tmpdir(),'lbry-lifecycle-'));
  instance = createApp({dataDir:dir,adminHash:passwordHash(password),clock:()=>now});
  server = await new Promise(resolve => { const s=instance.app.listen(0,'127.0.0.1',()=>resolve(s)); });
  base=`http://127.0.0.1:${server.address().port}`;
  image=(await sharp({create:{width:1280,height:200,channels:3,background:'#114433'}}).png().toBuffer()).toString('base64');
  const r=await request('/api/login',{password},false);cookie=r.headers.get('set-cookie').split(';')[0];csrf=(await r.json()).csrf;
});
after(async()=>{await new Promise(resolve=>server.close(resolve));instance.close();rmSync(dir,{recursive:true,force:true});});
test('delete revokes delivery and receipts, retains reports and supports safe restore', async()=>{
  const id=await create();
  await request(`/api/campaigns/${id}/complimentary`,{reason:'Local testing only'});
  await request(`/api/campaigns/${id}/approve`,{});
  const ad=await (await request('/v1/banner',undefined,false)).json();
  assert.equal((await fetch(base+'/v1/events',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:ad.event_token,type:'impression'})})).status,204);
  assert.equal((await request(`/api/campaigns/${id}/delete`,{},false)).status,401);
  assert.equal((await request(`/api/campaigns/${id}/delete`,{},true,'wrong')).status,403);
  assert.equal((await request(`/api/campaigns/${id}/delete`,{})).status,200);
  assert.equal((await request('/v1/banner',undefined,false)).status,204);
  assert.equal((await request(`/v1/images/${id}`,undefined,false)).status,404);
  assert.equal((await fetch(base+'/v1/events',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({token:ad.event_token,type:'click'})})).status,400);
  assert.equal((await (await request('/api/campaigns')).json()).campaigns.length,0);
  assert.equal((await (await request('/api/campaigns?deleted=1')).json()).campaigns[0].id,id);
  const report=await (await request('/api/analytics?period=daily')).json();
  assert.equal(report.campaigns.find(c=>c.id===id).impressions,1);
  assert.equal((await request(`/api/campaigns/${id}/approve`,{})).status,409);
  assert.equal((await request(`/api/campaigns/${id}/restore`,{},false)).status,401);
  assert.equal((await request(`/api/campaigns/${id}/restore`,{})).status,200);
  assert.equal((await request('/v1/banner',undefined,false)).status,204);
  const restored=(await (await request('/api/campaigns')).json()).campaigns.find(c=>c.id===id);
  assert.equal(restored.status,'paused');assert.equal(restored.deleted_at,null);
});
test('renewal validates expiry, dates and auth, copies creative without old payment or counters',async()=>{
  const id=await create(), tx='e'.repeat(64);
  await request(`/api/campaigns/${id}/payment`,{txid:tx,confirmed:true});
  const renew=()=>({starts:now,ends:now+86400000,amount:'200'});
  assert.equal((await request(`/api/campaigns/${id}/renew`,renew())).status,409);
  now+=11000;
  assert.equal((await request(`/api/campaigns/${id}/renew`,renew(),false)).status,401);
  assert.equal((await request(`/api/campaigns/${id}/renew`,renew(),true,'bad')).status,403);
  for(const patch of [{ends:now-1},{starts:now+100000000},{amount:'-1'},{amount:'NaN'},{ends:9007199254740991}])
    assert.equal((await request(`/api/campaigns/${id}/renew`,{...renew(),...patch})).status,400);
  const r=await request(`/api/campaigns/${id}/renew`,renew());assert.equal(r.status,201);const fresh=(await r.json()).id;
  assert.equal((await request(`/api/campaigns/${id}/renew`,renew())).status,409);
  const rows=(await (await request('/api/campaigns')).json()).campaigns;
  const old=rows.find(c=>c.id===id), next=rows.find(c=>c.id===fresh);
  assert.equal(old.txid,tx);assert.equal(next.txid,null);assert.equal(next.complimentary_reason,null);
  assert.equal(next.status,'pending');assert.equal(next.renewed_from,id);assert.equal(next.amount,'200');
  assert.equal(next.destination,old.destination);
  assert.deepEqual(await (await request(`/v1/images/${id}`)).arrayBuffer(),await (await request(`/v1/images/${fresh}`)).arrayBuffer());
  assert.equal((await request(`/api/campaigns/${fresh}/approve`,{})).status,409);
  assert.equal((await request(`/api/campaigns/${fresh}/payment`,{txid:tx,confirmed:true})).status,409);
  assert.equal((await request(`/api/campaigns/${fresh}/complimentary`,{reason:'Owner renewal test'})).status,200);
  assert.equal((await request(`/api/campaigns/${fresh}/approve`,{})).status,200);
  assert.equal((await (await request('/v1/banner',undefined,false)).json()).id,fresh);
  assert.equal((await request(`/api/campaigns/${id}/delete`,{})).status,200);
  assert.equal((await request(`/api/campaigns/${id}/renew`,renew())).status,409);
});
