// Demostración pequeña: dos clientes, créditos, cancelación, caché y reconexión.
const assert=require('node:assert/strict'),fs=require('node:fs');
const {launch,baseUrl}=require('./common.cjs');
(async()=>{
 const browser=await launch(),errors=[];let received=0,cancelled=0;
 try {
  const ctx=await browser.newContext({viewport:{width:1440,height:1000}});
  const a=await ctx.newPage(),b=await ctx.newPage();
  for(const p of [a,b])p.on('pageerror',e=>errors.push(e.message));
  a.on('websocket',ws=>ws.on('framereceived',f=>{
   if(Buffer.isBuffer(f.payload))received+=f.payload.length;
   else if(JSON.parse(f.payload).type==='CANCELLED')cancelled++;
  }));
  const complete=p=>p.waitForFunction(()=>$('view-state').textContent==='Vista completa');
  const request=async(p,reset=false)=>{
   const id=await p.evaluate(reset=>{if(reset)cache.invalidate(current.imageId,'',true);level=0;magnification=1;x=y=0;clearTimeout(timer);requestView();return viewId;},reset);
   await p.waitForFunction(id=>viewId===id&&$('view-state').textContent==='Vista completa',id);
  };
  await Promise.all([a.goto(baseUrl),b.goto(baseUrl)]);await Promise.all([complete(a),complete(b)]);
  await a.waitForTimeout(250);await complete(a);
  await request(a,true);await request(a);
  const repeated=await a.evaluate(()=>({modes:{...modes},bytes:pribBytes,verified,expected}));
  assert.equal(repeated.modes.REUSE,repeated.expected);assert.equal(repeated.modes.FULL,0);
  // La fixture pequeña necesita varias vistas sin concesiones para agotar 256 KiB.
  async function exhaust() {
   await a.evaluate(()=>{grantsPaused=true;});
   for(let i=0;i<6;i++) {
    await a.evaluate(()=>{cache.invalidate(current.imageId,'',true);level=0;magnification=1;x=y=0;requestView();});
    await a.waitForFunction(()=>$('credit-state').textContent==='Esperando crédito'||$('view-state').textContent==='Vista completa');
    if(await a.evaluate(()=>$('credit-state').textContent==='Esperando crédito'))return;
   }
   throw Error('La ventana no se agotó');
  }
  await exhaust();const stopped=received;
  await request(b,true);await a.waitForTimeout(300);assert.equal(received,stopped);
  const paused=await a.evaluate(()=>({state:$('credit-state').textContent,outstanding:$('credit-outstanding').textContent}));
  await a.evaluate(()=>{grantsPaused=false;grantCapacity(true);});await complete(a);
  await a.waitForFunction(()=>$('credit-outstanding').textContent==='0.0 KiB');
  await a.click('#toggle-info');await a.waitForTimeout(250);await complete(a);
  await a.screenshot({path:'build/browser-check/etapa-8-creditos.png',fullPage:true});
  await a.evaluate(()=>{for(let i=0;i<12;i++){x=i*13;requestView();}});await complete(a);
  await a.waitForFunction(()=>cache.holds.size===0);assert(cancelled>=12);
  const session=await a.evaluate(()=>sessionId);await exhaust();
  await a.evaluate(()=>connect());await complete(a);
  assert.notEqual(await a.evaluate(()=>sessionId),session);
  await a.waitForFunction(()=>$('credit-outstanding').textContent==='0.0 KiB');
  const final=await a.evaluate(()=>({verified,expected,cacheBytes:cache.bytes,holds:cache.holds.size,connection:$('connection').textContent,error:$('error').hidden?null:$('error').textContent}));
  assert.equal(final.verified,final.expected);assert.equal(final.holds,0);assert.equal(final.error,null);assert(final.cacheBytes<=16*1024*1024);
  assert.equal(await b.locator('#connection').textContent(),'Conectado');assert.deepEqual(errors,[]);
  fs.writeFileSync('build/demo-browser.json',JSON.stringify({repeated,paused,cancelled,final,slowClientStopped:true,otherClientCompleted:true,reconnectedWithDebt:true,errors},null,2)+'\n');
  console.log('PASS demo: caché REUSE, dos sesiones, WAIT_CREDIT, reanudación, 12 generaciones y reconexión con deuda');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
