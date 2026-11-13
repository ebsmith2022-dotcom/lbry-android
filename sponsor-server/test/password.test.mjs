import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,writeFileSync,readFileSync,existsSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createApp,passwordHash} from '../server.mjs';
test('password changes require current password and CSRF, revoke sessions, persist across restart',async()=>{
 const dataDir=mkdtempSync(join(tmpdir(),'sponsor-password-')), credentialFile=join(dataDir,'admin.json');
 const old='original-test-password', replacement='replacement-test-password';
 writeFileSync(credentialFile,JSON.stringify({hash:passwordHash(old)}));writeFileSync(join(dataDir,'first-login.txt'),'test only');
 let instance,server,base;
 async function start(){instance=createApp({dataDir,credentialFile,adminHash:JSON.parse(readFileSync(credentialFile)).hash});server=await new Promise(r=>{const s=instance.app.listen(0,'127.0.0.1',()=>r(s));});base=`http://127.0.0.1:${server.address().port}`;}
 async function stop(){await new Promise(r=>server.close(r));instance.close();}
 const req=(path,body,session={})=>fetch(base+path,{method:body?'POST':'GET',headers:{origin:base,'x-dashboard':'1','content-type':'application/json',...session},body:body?JSON.stringify(body):undefined});
 async function login(password){const r=await req('/api/login',{password});assert.equal(r.status,200);return {cookie:r.headers.get('set-cookie').split(';')[0],'x-csrf-token':(await r.json()).csrf};}
 await start();
 try {
  const a=await login(old),b=await login(old);
  assert.equal((await req('/api/password',{currentPassword:old,newPassword:replacement})).status,401);
  assert.equal((await req('/api/password',{currentPassword:old,newPassword:replacement},{...a,'x-csrf-token':'bad'})).status,403);
  assert.equal((await req('/api/password',{currentPassword:'wrong',newPassword:replacement},a)).status,400);
  assert.equal((await req('/api/password',{currentPassword:old,newPassword:'short'},a)).status,400);
  assert.equal((await req('/api/password',{currentPassword:old,newPassword:replacement},a)).status,200);
  assert.equal((await req('/api/session',null,a)).status,401);assert.equal((await req('/api/session',null,b)).status,401);
  assert.equal(existsSync(join(dataDir,'first-login.txt')),false);
  assert.ok(!readFileSync(credentialFile,'utf8').includes(replacement));
  await stop();await start();
  assert.equal((await req('/api/login',{password:old})).status,401);await login(replacement);
 }finally{await stop();rmSync(dataDir,{recursive:true,force:true});}
});
