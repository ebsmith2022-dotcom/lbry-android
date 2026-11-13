import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createApp,passwordHash} from '../server.mjs';
test('shared likes are idempotent, scoped by video and voter, reversible and persistent',async()=>{
 const dataDir=mkdtempSync(join(tmpdir(),'community-likes-'));let instance,server,base;
 async function start(){instance=createApp({dataDir,adminHash:passwordHash('test-only')});server=await new Promise(r=>{const s=instance.app.listen(0,'127.0.0.1',()=>r(s));});base=`http://127.0.0.1:${server.address().port}`;}
 async function stop(){await new Promise(r=>server.close(r));instance.close();}
 const claim='a'.repeat(40),other='b'.repeat(40),one='1'.repeat(64),two='2'.repeat(64);
 const req=(id,token,liked,extra={})=>fetch(base+'/v1/likes/'+id,{method:liked===undefined?'GET':'POST',headers:{'content-type':'application/json',...(token?{'x-like-token':token}:{}),...extra},body:liked===undefined?undefined:JSON.stringify({liked})});
 await start();try{
  assert.deepEqual(await (await req(claim,one)).json(),{count:0,liked:false});
  for(let i=0;i<3;i++)assert.deepEqual(await (await req(claim,one,true)).json(),{count:1,liked:true});
  assert.deepEqual(await (await req(claim,two)).json(),{count:1,liked:false});
  await req(claim,two,true);assert.deepEqual(await (await req(claim)).json(),{count:2,liked:false});
  await Promise.all(Array.from({length:8},()=>req(claim,one,true)));
  assert.equal((await (await req(claim)).json()).count,2);
  assert.deepEqual(await (await req(other,one)).json(),{count:0,liked:false});
  assert.equal((await req(claim,null,true)).status,400);
  assert.equal((await req('bad',one,true)).status,400);
  assert.equal((await req(claim,one,'true')).status,400);
  assert.equal((await req(claim,one,false,{origin:'https://evil.example'})).status,403);
  await stop();await start();assert.deepEqual(await (await req(claim,one)).json(),{count:2,liked:true});
  for(let i=0;i<2;i++)assert.deepEqual(await (await req(claim,one,false)).json(),{count:1,liked:false});
  await req(claim,two,false);assert.equal((await (await req(claim)).json()).count,0);
 }finally{await stop();rmSync(dataDir,{recursive:true,force:true});}
});
