// Evaluación offline del visor real. Requiere servidor, Chrome y Playwright locales.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const {launch, baseUrl} = require('./common.cjs');
const rows=[], errors=[], credits=[], cancellations=[], completions=[];
let peakCache=0, peakEntries=0, peakClientHeap=0;
function instrument(page) {
  const wire={binary:0, control:0, sentControl:0, modes:{}, fullEquivalent:0, packets:0, lastDone:null};
  page.on('pageerror', e=>errors.push(e.message));
  page.on('websocket', ws=>{
    ws.on('framesent', f=>{wire.sentControl+=Buffer.byteLength(f.payload);});
    ws.on('framereceived', f=>{
      if (Buffer.isBuffer(f.payload)) {
        const b=f.payload, n=b.readUInt32BE(0), h=JSON.parse(b.subarray(4,4+n));
        wire.binary+=b.length;wire.packets++;
        const mode=wire.modes[h.mode] ||= {packets:0,bytes:0};mode.packets++;mode.bytes+=b.length;
        // FULL hipotético por cada transmisión, conserva IDs y geometría del mensaje.
        const full={...h,type:'BLOCK_FULL',mode:'FULL',codec:'RAW',payloadLength:h.width*h.height*3};
        delete full.baseImageId;delete full.baseId;delete full.baseHash;
        wire.fullEquivalent+=4+Buffer.byteLength(JSON.stringify(full))+full.payloadLength;
      } else {
        wire.control+=Buffer.byteLength(f.payload);
        const h=JSON.parse(f.payload);
        if(h.type==='CREDIT_STATUS')credits.push(h);
        if(h.type==='CANCELLED')cancellations.push(h);
        if(h.type==='VIEW_DONE'){completions.push(h);wire.lastDone=h;}
      }
    });
  });return wire;
}
const snapshot=w=>JSON.parse(JSON.stringify(w));
const complete=p=>p.waitForFunction(()=>document.querySelector('#view-state').textContent==='Vista completa',null,{timeout:60000});
async function settle(p) {await complete(p);await p.waitForTimeout(250);await complete(p);}
async function view(page,wire,image,fx,fy,scenario,reset=false,scale=1) {
  const before=snapshot(wire),start=performance.now();
  const id=await page.evaluate(({image,fx,fy,reset,scale})=>{
    if(reset)cache.invalidate(current.imageId,'',true);
    current=images.find(i=>i.name===image);if(!current)throw Error('Imagen no encontrada');
    $('image').value=current.imageId;level=0;magnification=scale;
    // La captura debe mostrar el mismo nivel que solicita la prueba.
    $('level').value=String(level);
    x=Math.floor(fx*current.width);y=Math.floor(fy*current.height);clearTimeout(timer);requestView();return viewId;
  },{image,fx,fy,reset,scale});
  await page.waitForFunction(id=>viewId===id&&document.querySelector('#view-state').textContent==='Vista completa',id,{timeout:60000});
  const latencyMs=performance.now()-start;
  const client=await page.evaluate(()=>({verified,expected,modes:{...modes},pribBytes,recoveries,
    metrics:viewMetrics.snapshot(),
    cacheBytes:cache.bytes,entries:cache.entries.size,holds:cache.holds.size,region:{...activeView},
    heap:performance.memory?.usedJSHeapSize || null,error:$('error').hidden?null:$('error').textContent}));
  assert.equal(client.error,null);assert.equal(client.verified,client.expected);assert(client.verified>0);
  assert.equal(client.holds,0);assert(client.cacheBytes<=16*1024*1024);assert(client.entries<=1024);
  assert(client.metrics.firstBlockMs>=0 && client.metrics.firstBlockMs<=client.metrics.elapsedMs);
  assert(client.metrics.cacheHitRate>=0 && client.metrics.cacheHitRate<=1);
  assert(client.metrics.cacheAverageBytes>=0 && client.metrics.cacheAverageBytes<=16*1024*1024);
  peakCache=Math.max(peakCache,client.cacheBytes);peakEntries=Math.max(peakEntries,client.entries);peakClientHeap=Math.max(peakClientHeap,client.heap||0);
  const after=snapshot(wire),modeBytes={};
  for(const [m,v] of Object.entries(after.modes))modeBytes[m]={packets:v.packets-(before.modes[m]?.packets||0),bytes:v.bytes-(before.modes[m]?.bytes||0)};
  const row={scenario,image,latencyMs,client,binaryBytes:after.binary-before.binary,
    serverControlBytes:after.control-before.control,clientControlBytes:after.sentControl-before.sentControl,
    fullEquivalentBytes:after.fullEquivalent-before.fullEquivalent,modeBytes};
  assert.equal(row.binaryBytes,client.pribBytes);
  assert.equal(after.lastDone.viewId,id);row.server=after.lastDone;
  row.savingFraction=1-row.binaryBytes/row.fullEquivalentBytes;rows.push(row);return row;
}
function diskInventory() {
  const data=process.env.PRIB_DATA || 'data';
  return fs.readdirSync(data,{withFileTypes:true}).filter(d=>d.isDirectory()).map(d=>{
    const dir=data+'/'+d.name, meta=dir+'/image.properties';if(!fs.existsSync(meta))return null;
    const properties=Object.fromEntries(fs.readFileSync(meta,'utf8').split(/\r?\n/).filter(l=>l&&!l.startsWith('#')).map(l=>{const i=l.indexOf('=');return [l.slice(0,i),l.slice(i+1)];}));
    const files=fs.readdirSync(dir).filter(n=>fs.statSync(dir+'/'+n).isFile());
    return {name:d.name,width:Number(properties.width),height:Number(properties.height),levels:Number(properties.levels),
      files:files.length,diskBytes:files.reduce((n,f)=>n+fs.statSync(dir+'/'+f).size,0),rgbLevel0Bytes:Number(properties.width)*Number(properties.height)*3};
  }).filter(Boolean);
}
(async()=>{
 const browser=await launch();let report;
 try {
  const context=await browser.newContext({viewport:{width:1440,height:1000}});
  const page=await context.newPage(), wire=instrument(page);await page.goto(baseUrl);await settle(page);
  const catalog=await page.evaluate(()=>images.map(i=>({name:i.name,width:i.width,height:i.height})));
  assert(catalog.length>0);
  for(const image of catalog) {
    for(const [label,fx,fy] of [['origen',0,0],['centro',.5,.5],['borde',1,1]]) {
      await view(page,wire,image.name,fx,fy,'fria-'+label,true);
      await view(page,wire,image.name,fx,fy,'repetida-'+label);
    }
    console.log('PASS evaluación regiones:',image.name);
  }
  const largest=catalog.reduce((a,b)=>a.width*a.height>b.width*b.height?a:b);
  for(let i=0;i<40;i++)await view(page,wire,largest.name,((i*37)%97)/97,((i*53)%97)/97,'navegacion-40');
  const pages=[page],wires=[wire];
  for(let i=1;i<4;i++){const p=await context.newPage();pages.push(p);wires.push(instrument(p));await p.goto(baseUrl);await settle(p);}
  for(let round=0;round<4;round++)await Promise.all(pages.map((p,i)=>view(p,wires[i],largest.name,((round+i)*.19)%1,((round*2+i)*.17)%1,'concurrencia-4',true)));
  // Una ventana sin devoluciones debe detenerse, manteniendo independientes otros clientes.
  await page.evaluate(()=>{grantsPaused=true;cache.invalidate(current.imageId,'',true);level=0;magnification=1;x=0;y=0;requestView();});
  await page.waitForFunction(()=>$('credit-state').textContent==='Esperando crédito');
  const stopped=wire.binary;
  await Promise.all(pages.slice(1).map((p,i)=>view(p,wires[i+1],largest.name,.65,.7,'otros-con-cliente-pausado',true)));
  assert.equal(wire.binary,stopped);
  await page.evaluate(()=>{grantsPaused=false;grantCapacity(true);});await complete(page);
  await page.waitForFunction(()=>$('credit-outstanding').textContent==='0.0 KiB');
  const cancelledBefore=cancellations.length;
  await page.evaluate(()=>{for(let i=0;i<32;i++){x=(i*2113)%current.width;y=(i*1877)%current.height;requestView();}});
  await complete(page);await page.waitForFunction(()=>cache.holds.size===0);
  assert(cancellations.length>cancelledBefore);
  const oldSession=await page.evaluate(()=>sessionId);
  await page.evaluate(()=>{grantsPaused=true;cache.invalidate(current.imageId,'',true);x=0;y=0;requestView();});
  await page.waitForFunction(()=>$('credit-state').textContent==='Esperando crédito');
  await page.evaluate(()=>connect());await settle(page);assert.notEqual(await page.evaluate(()=>sessionId),oldSession);
  await page.waitForFunction(()=>$('credit-outstanding').textContent==='0.0 KiB');
  // Corrupción de un buffer de recepción: recuperación selectiva real en imagen grande.
  await page.evaluate(()=>{
    const original=PribDelta.reconstruct;
    PribDelta.reconstruct=async function(h,p,c,d){
      if(h.mode!=='FULL')return original(h,p,c,d);
      PribDelta.reconstruct=original;const corrupted=p.slice();corrupted[0]^=1;
      return original(h,corrupted,c,d);
    };
  });
  const recovered=await view(page,wire,largest.name,.35,.45,'recuperacion-hash',true);
  assert.equal(recovered.client.recoveries,1);assert.equal(recovered.server.recoveries,1);
  // Captura de detalle a 400 %, visible y validado, en la imagen mayor.
  await view(page,wire,largest.name,1,1,'detalle-400',false,4);
  await page.screenshot({path:'build/browser-check/etapa-7-detalle.png',fullPage:true});
  assert.deepEqual(errors,[]);
  for(const c of credits){assert(c.availableBytes>=0);assert(c.outstandingBytes>=0);assert(c.availableBytes<=c.capacityBytes);assert(c.outstandingBytes<=c.capacityBytes);}
  const percentile=(v,p)=>[...v].sort((a,b)=>a-b)[Math.ceil(v.length*p)-1];
  const groups={};for(const row of rows)(groups[row.scenario] ||= []).push(row);
  const summary=Object.fromEntries(Object.entries(groups).map(([k,v])=>[k,{views:v.length,p50Ms:percentile(v.map(r=>r.latencyMs),.5),p95Ms:percentile(v.map(r=>r.latencyMs),.95),
    binaryBytes:v.reduce((n,r)=>n+r.binaryBytes,0),fullEquivalentBytes:v.reduce((n,r)=>n+r.fullEquivalentBytes,0)}]));
  report={schema:1,date:new Date().toISOString(),environment:{platform:os.platform(),release:os.release(),cpu:os.cpus()[0].model,logicalCpus:os.cpus().length,totalMemory:os.totalmem(),node:process.version,browser:browser.version(),viewport:{width:1440,height:1000}},
    definition:'Latencia desde invocación Playwright hasta VIEW_DONE procesado con SHA-256; bytes PRIB incluyen encabezado, excluyen framing WebSocket/TCP/HTTP. FULL equivalente por transmisión, no benchmark de otro códec. FULL equivalente incluye también los reintentos, y no representa una ejecución sin errores ni controles. Cache fría es inventario vacío; no vacía la caché de disco del SO.',
    catalog:diskInventory(),rows,summary,resources:{peakCacheBytes:peakCache,peakCacheEntries:peakEntries,peakObservedClientJsHeapBytes:peakClientHeap},
    stress:{clients:4,longNavigationViews:40,rapidRequestedViews:32,cancellations:cancellations.length,cancelledTasks:cancellations.reduce((n,c)=>n+(c.cancelledTasks||0),0),unsentBytes:cancellations.reduce((n,c)=>n+(c.unsentBytes||0),0),creditSamples:credits.length,slowClientStopped:true,othersProgressed:true,reconnectedWithOutstanding:true,selectiveHashRecovery:true,recoveries:rows.reduce((n,r)=>n+r.client.recoveries,0),errors},
    modes:Object.fromEntries(['FULL','REUSE','REF','DELTA'].map(m=>[m,{packets:wires.reduce((n,w)=>n+(w.modes[m]?.packets||0),0),bytes:wires.reduce((n,w)=>n+(w.modes[m]?.bytes||0),0)}]))};
  fs.writeFileSync('build/evaluation-stage7.json',JSON.stringify(report,null,2)+'\n');
  console.log('PASS etapa 7:',JSON.stringify({summary,resources:report.resources,stress:report.stress,modes:report.modes}));
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
