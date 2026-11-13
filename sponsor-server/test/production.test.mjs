import http from 'node:http';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createApp, passwordHash } from '../server.mjs';
test('production enforces canonical host/origin, secure cookies and proxy client throttling', async () => {
 const dataDir = mkdtempSync(join(tmpdir(), 'sponsors-production-'));
 const password = 'isolated-test-only';
 const origin = 'https://ads.example.com';
 const instance = createApp({dataDir,adminHash:passwordHash(password),secure:true,publicOrigin:origin});
 const server = await new Promise(resolve => {const s=instance.app.listen(0,'127.0.0.1',()=>resolve(s));});
 const base = `http://127.0.0.1:${server.address().port}`;
 const request = (path, body, extra={}) => new Promise((resolve,reject)=>{
  const req=http.request(base+path,{method:body?'POST':'GET',headers:{host:'ads.example.com',origin,'x-dashboard':'1','content-type':'application/json','x-forwarded-for':'192.0.2.10',...extra}},res=>{res.resume();res.on('end',()=>resolve({status:res.statusCode,headers:{get:name=>Array.isArray(res.headers[name])?res.headers[name][0]:res.headers[name]}}));});
  req.on('error',reject);req.end(body?JSON.stringify(body):undefined);
 });
 try {
  assert.equal((await request('/api/session')).status,401);
  assert.equal((await request('/api/login',{password},{host:'evil.example.com',origin:'https://evil.example.com'})).status,421);
  assert.equal((await request('/api/login',{password},{origin:'https://evil.example.com'})).status,403);
  const response=await request('/api/login',{password});
  assert.equal(response.status,200);
  assert.match(response.headers.get('set-cookie'),/; Secure/);
  const cookie=response.headers.get('set-cookie').split(';')[0];
  assert.equal((await request('/api/session',null,{cookie})).status,200);
  for(let i=0;i<5;i++) assert.equal((await request('/api/login',{password:'wrong'})).status,401);
  assert.equal((await request('/api/login',{password})).status,429);
  assert.equal((await request('/api/login',{password},{'x-forwarded-for':'192.0.2.11'})).status,200);
 } finally {await new Promise(resolve=>server.close(resolve));instance.close();rmSync(dataDir,{recursive:true,force:true});}
});
