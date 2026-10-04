// FULL/REUSE, utilidad limitada y cancelaciones sobre cualquier almacén pequeño.
const assert = require('node:assert/strict');
const {launch, baseUrl} = require('./common.cjs');
(async () => {
  const browser = await launch(), errors = [];
  try {
    const context = await browser.newContext({viewport:{width:1440,height:1000}});
    const a = await context.newPage(), b = await context.newPage();
    for (const page of [a,b]) page.on('pageerror', e => errors.push(e.message));
    const complete = p => p.waitForFunction(() => document.querySelector('#view-state').textContent === 'Vista completa');
    await Promise.all([a.goto(baseUrl),b.goto(baseUrl)]); await Promise.all([complete(a),complete(b)]);
    await a.waitForTimeout(250); await complete(a);
    const baseline = await a.evaluate(() => ({bytes:pribBytes, full:modes.FULL, blocks:verified}));
    assert(baseline.full > 0);
    const before = await a.evaluate(() => viewId);
    await a.click('#go');
    await a.waitForFunction(id => viewId > id && document.querySelector('#view-state').textContent === 'Vista completa', before);
    const repeated = await a.evaluate(() => ({bytes:pribBytes, full:modes.FULL, reuse:modes.REUSE, blocks:verified, cache:cache.bytes}));
    assert.equal(repeated.full,0); assert.equal(repeated.reuse,repeated.blocks); assert(repeated.bytes < baseline.bytes);
    assert(repeated.cache <= 16*1024*1024);
    if (process.env.PRIB_REF_IMAGE) {
      await a.selectOption('#image',{label:process.env.PRIB_REF_IMAGE}); await complete(a);
      const result = await a.evaluate(() => ({ref:modes.REF, full:modes.FULL, blocks:verified}));
      assert.equal(result.ref,result.blocks); assert.equal(result.full,0);
      console.log('PASS navegador REF: otra imagen con contenido y geometría idénticos');
    }
    // Cada nueva generación protege el inventario antes de cancelar la anterior.
    await a.evaluate(() => { for(let i=0;i<12;i++)requestView(); }); await complete(a);
    await a.waitForFunction(() => cache.holds.size === 0);
    assert.equal(await a.locator('#error').isVisible(),false);
    assert.equal(await b.locator('#connection').textContent(),'Conectado');
    await a.waitForFunction(() => document.querySelector('#credit-outstanding').textContent === '0.0 KiB');
    const session = await a.locator('#session').textContent();
    await a.click('#reconnect'); await complete(a);
    assert.notEqual(await a.locator('#session').textContent(), session);
    assert((await a.evaluate(() => modes.FULL)) > 0);
    assert.deepEqual(errors,[]);
    await a.click('#toggle-info'); await a.waitForTimeout(250); await complete(a);
    await a.screenshot({path:'build/browser-check/etapa-5-reuse.png',fullPage:true});
    console.log('PASS navegador etapa 5:', JSON.stringify({baseline,repeated}), 'generaciones, créditos, dos clientes y reconexión');
  } finally { await browser.close(); }
})().catch(e => {console.error(e);process.exitCode=1;});
