// Prueba de desarrollo: requiere servidor activo e imagen de 17 GB preparada.
const assert=require('node:assert/strict');
const {launch, baseUrl, imageName} = require('./common.cjs');
(async()=>{
 const browser=await launch();
 try {
 const ctx=await browser.newContext({viewport:{width:1440,height:1000}});
 const a=await ctx.newPage(),b=await ctx.newPage(),errors=[];let binaryBytes=0;
 for(const p of [a,b])p.on('pageerror',e=>errors.push(e.message));
 a.on('websocket',ws=>ws.on('framereceived',f=>{if(Buffer.isBuffer(f.payload))binaryBytes+=f.payload.length;}));
 const complete=p=>p.waitForFunction(()=>document.querySelector('#view-state').textContent==='Vista completa');
 const settle=async p=>{await p.waitForTimeout(350);await complete(p);};
 await Promise.all([a.goto(baseUrl),b.goto(baseUrl)]);
 await Promise.all([complete(a),complete(b)]);
 await a.selectOption('#image',{label:imageName});await settle(a);
 await a.click('#toggle-info');await settle(a);
 await a.locator('#demo-tools summary').click();
 await a.click('#pause-credit');
 await a.selectOption('#level','0');
 await a.waitForFunction(()=>document.querySelector('#credit-state').textContent==='Esperando crédito');
 const stopped=binaryBytes;await a.waitForTimeout(450);assert.equal(binaryBytes,stopped);
 await b.selectOption('#level','0');await settle(b);
 assert.equal(await b.locator('#connection').textContent(),'Conectado');
 // Navegar mientras faltan créditos no inventa capacidad.
 await a.fill('#x','74000');await a.fill('#y','74000');await a.click('#go');
 await a.waitForTimeout(400);assert.equal(binaryBytes,stopped);
 await a.click('#pause-credit');await complete(a);
 await a.waitForFunction(()=>document.querySelector('#credit-outstanding').textContent==='0.0 KiB');
 const metrics=await a.evaluate(()=>viewMetrics.snapshot());
 assert(metrics.waitCreditMs>=300);assert(metrics.firstBlockMs>=0);assert(metrics.grants>0);
 assert.equal(await a.locator('#credit-available').textContent(),'256.0 KiB');
 // Reenviar la última concesión desde el cliente no aumenta el saldo.
 await a.evaluate(()=>send('CREDIT_GRANT',{grantId,releasedBytes}));
 await a.evaluate(()=>send('CREDIT_STATUS'));
 await a.waitForTimeout(200);assert.equal(await a.locator('#credit-available').textContent(),'256.0 KiB');
 const session=await a.locator('#session').textContent();
 // Reconectar con bytes pendientes conserva la región, pero elimina la deuda vieja.
 await a.click('#pause-credit');await a.fill('#x','70000');await a.click('#go');
 await a.waitForFunction(()=>document.querySelector('#credit-state').textContent==='Esperando crédito');
 await a.click('#reconnect');await settle(a);
 assert.notEqual(await a.locator('#session').textContent(),session);
 assert.equal(await a.locator('#x').inputValue(),'70000');
 await a.waitForFunction(()=>document.querySelector('#credit-outstanding').textContent==='0.0 KiB');
 assert.equal(await a.locator('#error').isVisible(),false);
 assert.equal(await a.locator('#integrity').textContent(),'SHA-256 correcto');
 await a.screenshot({path:'build/browser-check/etapa-4-creditos.png'});
 assert.deepEqual(errors,[]);
 console.log('PASS navegador: pausa real, región nueva bloqueada, reanudación, duplicado, cliente independiente y reconexión sin deuda');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
