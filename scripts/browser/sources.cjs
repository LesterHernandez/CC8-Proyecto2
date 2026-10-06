// Ejecutar test.ps1 antes. Fixtures pequeñas; no descarga imágenes externas.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),http=require('node:http');
const {spawn,spawnSync}=require('node:child_process');const {launch}=require('./common.cjs');
(async()=>{
 const fixture=JSON.parse(fs.readFileSync(process.env.PRIB_SOURCE_FIXTURE || 'build/sources-fixture.json','utf8'));
 const zip=JSON.parse(fs.readFileSync('build/preparation-fixture.json','utf8'));
 const input=fs.readFileSync(fixture.png),data=fs.mkdtempSync('build/web-sources-');
 const remote=http.createServer((req,res)=>{if(req.url==='/redirect'){res.writeHead(302,{Location:'/image.png'});res.end();}else if(req.url==='/bad'){res.end('<html>Esto no es PNG</html>');}else{res.setHeader('Content-Type','image/png');res.end(input);}});
 await new Promise(resolve=>remote.listen(0,'127.0.0.1',resolve));
 const url='http://127.0.0.1:'+remote.address().port;
 const probe=spawnSync('java',['-XshowSettings:properties','-version'],{encoding:'utf8',windowsHide:true});
 const home=probe.stderr.match(/java.home\s*=\s*(.+)/)?.[1].trim();if(!home)throw Error('Java no disponible');
 const server=spawn(path.join(home,'bin',process.platform==='win32'?'java.exe':'java'),['-Xmx256m','-Dprib.allowLocalImageUrls=true','-cp','build/classes','prib.PribServer','0',data,zip.archives],{windowsHide:true});
 const stopped=new Promise(resolve=>server.once('exit',resolve));let browser;
 try {
  const port=await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('Servidor no inició')),15000);server.stdout.on('data',b=>{const m=b.toString().match(/localhost:(\d+)/);if(m){clearTimeout(timer);resolve(m[1]);}});server.once('error',reject);});
  browser=await launch();const page=await browser.newPage({viewport:{width:1440,height:1000}}),errors=[];
  page.on('pageerror',e=>errors.push(e.message));await page.goto('http://localhost:'+port);
  await page.waitForFunction(()=>document.querySelector('#connection').textContent==='Conectado');
  await page.click('#prepare-open');assert.equal(await page.inputValue('#prepare-source'),'zip');
  await page.selectOption('#prepare-source','file');await page.setInputFiles('#prepare-file',fixture.png);await page.fill('#prepare-name','local');
  await page.screenshot({path:'build/browser-check/fuente-png.png',fullPage:true});await page.click('#prepare-start');
  await page.waitForFunction(()=>document.querySelector('#image').options.length===1,null,{timeout:20000});
  await page.waitForFunction(()=>document.querySelector('#prepare-status').textContent.includes('Imagen preparada:'),null,{timeout:10000});
  await page.click('#prepare-close');await page.waitForFunction(()=>document.querySelector('#view-state').textContent==='Vista completa');
  async function exact(){const hashes=await page.evaluate(()=>Array.from(cache.entries.values()).filter(e=>e.header.level===0).map(e=>e.header.expectedHash));assert.ok(hashes.includes(fixture.hash),'RGB debe coincidir con fixture');}
  await exact();const other=await browser.newPage();await other.goto('http://localhost:'+port);await other.waitForFunction(()=>document.querySelector('#view-state').textContent==='Vista completa');
  await page.click('#prepare-open');await page.selectOption('#prepare-source','url');await page.fill('#prepare-url',url+'/redirect');await page.fill('#prepare-name','remota');
  await page.screenshot({path:'build/browser-check/fuente-url.png',fullPage:true});await page.click('#prepare-start');
  await page.waitForFunction(()=>document.querySelector('#image').options.length===2,null,{timeout:20000});
  await other.waitForFunction(()=>document.querySelector('#image').options.length===2);
  await page.waitForFunction(()=>!document.querySelector('#prepare-start').disabled);
  await page.fill('#prepare-name','html');await page.fill('#prepare-url',url+'/bad');await page.click('#prepare-start');
  await page.waitForFunction(()=>document.querySelector('#prepare-status').textContent.includes('Firma'),null,{timeout:10000});
  assert.equal(await page.textContent('#connection'),'Conectado');
  await page.waitForFunction(()=>!document.querySelector('#prepare-start').disabled);await page.fill('#prepare-name','recuperada');await page.fill('#prepare-url',url+'/image.png');await page.click('#prepare-start');
  await page.waitForFunction(()=>document.querySelector('#image').options.length===3,null,{timeout:20000});
  await page.waitForFunction(()=>!document.querySelector('#prepare-start').disabled);await page.fill('#prepare-name','local');await page.click('#prepare-start');
  await page.waitForFunction(()=>document.querySelector('#prepare-status').textContent.includes('ya existe'));
  await page.selectOption('#prepare-source','zip');assert.ok(await page.locator('#prepare-zip').isVisible());await page.waitForFunction(()=>document.querySelector('#prepare-entry').options.length===2);
  await page.click('#prepare-close');await page.selectOption('#image',{label:'remota'});await page.waitForFunction(()=>document.querySelector('#view-state').textContent==='Vista completa');await exact();
  assert.deepEqual(errors,[]);assert.equal(fs.existsSync(path.join(data,'image.png')),false);
  console.log('PASS navegador: imagen local, URL/redirección, RGB exacto, error y reintento, no sobrescritura, dos clientes y opción ZIP preservada');
 }finally{if(browser)await browser.close();server.kill();await stopped;await new Promise(resolve=>remote.close(resolve));}
})().catch(e=>{console.error(e);process.exitCode=1;});
