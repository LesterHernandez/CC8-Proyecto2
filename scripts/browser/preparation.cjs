// Prueba aislada de preparación web. Ejecutar test.ps1 antes para generar el ZIP sintético.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const {spawn,spawnSync}=require('node:child_process');
const {launch}=require('./common.cjs');
(async()=>{
  const fixture=JSON.parse(fs.readFileSync('build/preparation-fixture.json','utf8'));
  const data=fs.mkdtempSync('build/web-preparation-');
  const probe=spawnSync(process.env.PRIB_JAVA||'java',['-XshowSettings:properties','-version'],{encoding:'utf8',windowsHide:true});
  const home=probe.stderr.match(/java.home\s*=\s*(.+)/)?.[1].trim();
  if(!home)throw Error('Java no disponible');
  const java=path.join(home,'bin',process.platform==='win32'?'java.exe':'java');
  const server=spawn(java,['-Xmx256m','-cp','build/classes','prib.PribServer','0',data,fixture.archives],{windowsHide:true});
  const stopped=new Promise(resolve=>server.once('exit',resolve));let browser;
  try {
    const port=await new Promise((resolve,reject)=>{
      const timeout=setTimeout(()=>reject(Error('Servidor no inició')),15000);
      server.stdout.on('data',b=>{const m=b.toString().match(/localhost:(\d+)/);if(m){clearTimeout(timeout);resolve(m[1]);}});
      server.once('error',reject);server.once('exit',()=>{clearTimeout(timeout);reject(Error('Servidor terminó'));});
    });
    browser=await launch();const page=await browser.newPage({viewport:{width:1440,height:1000}}), errors=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto('http://localhost:'+port);
    await page.waitForFunction(()=>document.querySelector('#connection').textContent==='Conectado');
    assert.equal(await page.locator('#image option').count(),0);
    await page.click('#prepare-open');
    await page.waitForFunction(()=>document.querySelector('#prepare-entry').options.length===2);
    await page.selectOption('#prepare-entry','carpeta/imagen.png');
    await page.fill('#prepare-name','desde-web');
    await page.click('#prepare-start');
    // Cerrar el panel no cancela; otra pestaña también recibe el catálogo nuevo.
    await page.click('#prepare-close');
    const other=await browser.newPage();await other.goto('http://localhost:'+port);
    for(const p of [page,other])await p.waitForFunction(()=>document.querySelector('#view-state').textContent==='Vista completa',null,{timeout:20000});
    assert.equal(await page.locator('#image option').count(),1);
    assert.equal(await other.locator('#image option').count(),1);
    await page.click('#prepare-open');
    await page.waitForFunction(()=>document.querySelector('#prepare-status').textContent.includes('Imagen preparada:'));
    await page.waitForFunction(()=>document.querySelector('#prepare-entry').options.length===2);
    await page.selectOption('#prepare-entry','carpeta/imagen.png');
    await page.click('#prepare-start');
    await page.waitForFunction(()=>document.querySelector('#prepare-status').textContent.includes('ya existe'),null,{timeout:10000});
    assert.equal(await page.locator('#connection').textContent(),'Conectado');
    await page.selectOption('#prepare-entry','incorrecta.png');await page.fill('#prepare-name','invalida');await page.click('#prepare-start');
    await page.waitForFunction(()=>document.querySelector('#prepare-status').textContent.includes('Firma'),null,{timeout:10000});
    await page.selectOption('#prepare-entry','carpeta/imagen.png');await page.fill('#prepare-name','segunda');await page.click('#prepare-start');
    await page.waitForFunction(()=>document.querySelector('#image').options.length===2,null,{timeout:20000});
    await page.click('#prepare-close');await page.selectOption('#image',{label:'segunda'});
    await page.waitForFunction(()=>document.querySelector('#view-state').textContent==='Vista completa');
    await page.click('#reconnect');await page.waitForFunction(()=>document.querySelector('#view-state').textContent==='Vista completa');
    assert.equal(await page.locator('#image option').count(),2);
    assert.deepEqual(errors,[]);
    await page.click('#prepare-open');await page.waitForTimeout(300);
    fs.mkdirSync('build/browser-check',{recursive:true});
    await page.screenshot({path:'build/browser-check/preparacion-web.png',fullPage:true});
    console.log('PASS preparación web: catálogo vacío, ZIP, cierre de panel, dos clientes, errores, catálogo dinámico y reconexión');
  } finally {if(browser)await browser.close();server.kill();await stopped;}
})().catch(e=>{console.error(e);process.exitCode=1;});
