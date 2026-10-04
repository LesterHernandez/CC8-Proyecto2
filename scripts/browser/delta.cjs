// Servidor sobre la fixture 512x128 generada por DeltaProtocolTest.
const assert=require('node:assert/strict'),fs=require('node:fs');
const {launch,baseUrl}=require('./common.cjs');
(async()=>{
 const browser=await launch(),errors=[],reasons=[];
 try {
  const page=await browser.newPage({viewport:{width:1440,height:1000}});
  page.on('pageerror',e=>errors.push(e.message));
  page.on('websocket',ws=>ws.on('framesent',frame=>{try{const m=JSON.parse(frame.payload);if(m.type==='RECOVER')reasons.push(m.reason);}catch{}}));
  const complete=()=>page.waitForFunction(()=>document.querySelector('#view-state').textContent==='Vista completa');
  await page.goto(baseUrl);await complete();await page.waitForTimeout(200);await complete();
  assert.equal(await page.evaluate(()=>current.width),512);assert.equal(await page.evaluate(()=>current.height),128);
  await page.click('#toggle-info');await page.waitForTimeout(250);await complete();
  const expected=JSON.parse(fs.readFileSync('build/delta-expected.json','utf8'));
  const reset=()=>page.evaluate(()=>cache.invalidate(current.imageId,'',true));
  const region=async col=>{
   const id=await page.evaluate(col=>{level=0;magnification=16;x=col*128;y=0;requestView();return viewId;},col);
   await page.waitForFunction(id=>viewId===id&&document.querySelector('#view-state').textContent==='Vista completa',id);
   const hash=await page.evaluate(async col=>digest(cache.get(current.imageId,`0:${col}:0`).rgb),col);
   assert.equal(hash,expected[col]);
   return page.evaluate(()=>({modes:{...modes},bytes:pribBytes,recoveries,verified,expected,holds:cache.holds.size}));
  };
  await reset();const full=await region(0);assert.equal(full.modes.FULL,1);
  assert.equal((await region(1)).modes.REF,1);
  const delta=await region(2);assert.equal(delta.modes.DELTA,1);assert(delta.bytes<full.bytes/10);
  await page.screenshot({path:'build/browser-check/etapa-6-delta.png',fullPage:true});
  assert.equal((await region(0)).modes.REUSE,1);assert.equal((await region(3)).modes.FULL,1);
  for(const reason of ['BASE_MISSING','HASH_MISMATCH','DELTA_FAILED','CACHE_MISS']) {
   await reset();await region(0);
   await page.evaluate(reason=>{
    const original=PribDelta.reconstruct;
    PribDelta.reconstruct=async function(h,p,c,d){
     if(h.mode!=='DELTA')return original(h,p,c,d);
     PribDelta.reconstruct=original;
     if(reason==='BASE_MISSING')return original(h,p,{get:()=>undefined},d);
     if(reason==='DELTA_FAILED')return original(h,p.slice(0,-1),c,d);
     const base=c.get(h.baseImageId,h.baseId);
     if(reason==='HASH_MISMATCH')base.rgb[0]^=1;
     if(reason==='CACHE_MISS')base.header.expectedHash='0'.repeat(64);
     return original(h,p,c,d);
    };
   },reason);
   const recovered=await region(2);
   assert.equal(recovered.recoveries,1);assert.equal(recovered.modes.DELTA,1);assert.equal(recovered.modes.FULL,1);
   assert.equal(recovered.verified,1);assert.equal(recovered.holds,0);
   assert.equal(await page.locator('#connection').textContent(),'Conectado');
   assert.equal(await page.locator('#error').isVisible(),false);
   await page.waitForFunction(()=>document.querySelector('#credit-outstanding').textContent==='0.0 KiB');
  }
  assert.deepEqual(reasons,['BASE_MISSING','HASH_MISMATCH','DELTA_FAILED','CACHE_MISS']);assert.deepEqual(errors,[]);
  await page.screenshot({path:'build/browser-check/etapa-6-recuperacion.png',fullPage:true});
  console.log('PASS Chrome etapa 6:',JSON.stringify({full,delta}), 'cuatro modos, cuatro recuperaciones reales, RGB exacto y bases liberadas');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
