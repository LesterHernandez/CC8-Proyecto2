// Ejecutado por demo.ps1 -Red sobre un servidor temporal y la fixture sintética.
const assert = require('node:assert/strict');
const {launch, baseUrl} = require('./common.cjs');
(async () => {
  const browser = await launch(), errors = [];
  try {
    const localUrl = new URL(baseUrl); localUrl.hostname = 'localhost';
    const local = await browser.newPage(), remote = await browser.newPage();
    for (const page of [local, remote]) page.on('pageerror', e => errors.push(e.message));
    await Promise.all([local.goto(localUrl.href), remote.goto(baseUrl)]);
    for (const page of [local, remote]) {
      await page.waitForFunction(() => $('view-state').textContent === 'Vista completa');
      assert.equal(await page.locator('#integrity').textContent(), 'SHA-256 correcto');
    }
    assert.equal(await local.evaluate(() => !!globalThis.crypto?.subtle), true);
    assert.deepEqual(await remote.evaluate(() => [isSecureContext, !!globalThis.crypto?.subtle]), [false, false]);
    assert.equal(await remote.evaluate(() => socket.url), baseUrl.replace('http:', 'ws:') + '/ws');
    const localSession = await local.evaluate(() => sessionId);
    assert.notEqual(await remote.evaluate(() => sessionId), localSession);
    // Reconectar el cliente por IP no debe reiniciar la sesión que usa localhost.
    const remoteSession = await remote.evaluate(() => sessionId);
    await remote.evaluate(() => connect());
    await remote.waitForFunction(previous => sessionId !== previous && $('view-state').textContent === 'Vista completa', remoteSession);
    assert.equal(await local.evaluate(() => sessionId), localSession);
    assert.deepEqual(errors, []);
    console.log('PASS localhost + IPv4 simultáneos, SHA-256 sin Web Crypto, WebSocket dinámico y sesiones independientes');
  } finally { await browser.close(); }
})().catch(e => { console.error(e); process.exitCode = 1; });
