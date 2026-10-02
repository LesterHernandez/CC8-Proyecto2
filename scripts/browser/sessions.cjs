// Prueba de desarrollo: requiere servidor activo e imagen de 17 GB preparada.
const assert = require('node:assert/strict');
const {launch, baseUrl, imageName} = require('./common.cjs');
(async () => {
  const browser = await launch();
  const errors = [], external = [];
  try {
    const context = await browser.newContext({viewport: {width: 1440, height: 1100}});
    context.on('request', r => { if (!r.url().startsWith(baseUrl + '/')) external.push(r.url()); });
    const a = await context.newPage(), b = await context.newPage();
    for (const page of [a,b]) {
      page.on('pageerror', e => errors.push(e.message));
      page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    }
    const complete = page => page.waitForFunction(() => document.querySelector('#view-state').textContent === 'Vista completa');
    await Promise.all([a.goto(baseUrl), b.goto(baseUrl)]);
    await Promise.all([complete(a), complete(b)]);
    const sessionA = await a.locator('#session').innerText(), sessionB = await b.locator('#session').innerText();
    assert.notEqual(sessionA, sessionB);
    await a.locator('#image').selectOption({label:imageName}); await complete(a);
    await a.locator('#level').selectOption('0');
    await a.waitForFunction(() => document.querySelector('#dimensions').textContent.includes('nivel 0'));
    await complete(a);
    await a.locator('#x').fill('74000'); await a.locator('#y').fill('74000'); await a.locator('#go').click(); await complete(a);
    assert.equal(await a.locator('#integrity').innerText(), 'SHA-256 correcto');
    assert.equal(await b.locator('#session').innerText(), sessionB);
    assert.equal(await b.locator('#connection').innerText(), 'Conectado');
    console.log('PASS navegadores independientes; 17 GB:', await a.locator('#blocks').innerText(), await a.locator('#bytes').innerText());
    await a.screenshot({path:'build/browser-check/visor-17gb.png',fullPage:true});
    // Cambiar detalle rápidamente mientras se reciben bloques no debe cerrar la sesión.
    for (const level of ['1','0','2','0','1','0']) await a.locator('#level').selectOption(level);
    await a.waitForTimeout(250); await complete(a);
    const box = await a.locator('#canvas').boundingBox();
    await a.mouse.move(box.x+300, box.y+200); await a.mouse.down();
    await a.mouse.move(box.x+200, box.y+100, {steps: 8}); await a.mouse.up(); await complete(a);
    const maxX = await a.locator('#x').getAttribute('max'), maxY = await a.locator('#y').getAttribute('max');
    await a.locator('#x').fill(maxX); await a.locator('#y').fill(maxY); await a.locator('#go').click(); await complete(a);
    assert.equal(await a.locator('#error').isVisible(), false);
    console.log('PASS cambios rápidos, arrastre y borde final:', await a.locator('#blocks').innerText());
    await a.locator('#reconnect').click(); await complete(a);
    assert.notEqual(await a.locator('#session').innerText(), sessionA);
    await a.close();
    await b.locator('#level').selectOption('0'); await b.waitForTimeout(200); await complete(b);
    assert.equal(await b.locator('#connection').innerText(), 'Conectado');
    assert.equal(await b.locator('#error').isVisible(), false);
    assert.deepEqual(errors, []); assert.deepEqual(external, []);
    console.log('PASS reconexión, cliente restante activo, sin errores JS ni solicitudes externas');
  } finally { await browser.close(); }
})().catch(e => {console.error(e);process.exitCode=1;});
