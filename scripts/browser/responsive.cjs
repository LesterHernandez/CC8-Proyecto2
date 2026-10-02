// Prueba de desarrollo: requiere servidor activo e imagen de 17 GB preparada.
const assert=require('node:assert/strict');
const {launch, baseUrl, imageName} = require('./common.cjs');
(async()=>{
 const browser=await launch();
 try {
 const page=await browser.newPage({viewport:{width:1920,height:1080}});
 const errors=[]; page.on('pageerror',e=>errors.push(e.message));
 await page.goto(baseUrl);
 const complete=()=>page.waitForFunction(()=>document.querySelector('#view-state').textContent==='Vista completa');
 const settle=async()=>{await page.waitForTimeout(500);await complete();};
 await complete();
 await page.selectOption('#image',{label:imageName});await settle();
 await page.selectOption('#level','0');await settle();
 const width=await page.locator('#canvas').evaluate(c=>c.width); assert(width>1800);
 await page.click('#toggle-info');await settle();
 assert(await page.locator('#transfer').isVisible());
 assert(await page.locator('#canvas').evaluate(c=>c.width)<width);
 await page.click('#toggle-info');await settle();
 assert.equal(await page.locator('#canvas').evaluate(c=>c.width),width);
 await page.click('#fullscreen');await settle();
 assert(await page.evaluate(()=>!!document.fullscreenElement));
 await page.click('#fullscreen');await settle();
 assert.equal(await page.evaluate(()=>!!document.fullscreenElement),false);
 await page.screenshot({path:'build/browser-check/visor-ampliado.png'});
 await page.setViewportSize({width:3840,height:2160});await settle();
 assert(await page.locator('#canvas').evaluate(c=>c.width)>3700);
 assert.equal(await page.locator('#integrity').textContent(),'SHA-256 correcto');
 console.log('PASS vista 4K',await page.locator('#blocks').textContent());
 await page.setViewportSize({width:700,height:800});await settle();
 await page.click('#toggle-info');await settle();
 assert(await page.locator('#transfer').isVisible());
 assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
 assert.deepEqual(errors,[]);
 assert.equal(await page.locator('#error').isVisible(),false);
 console.log('PASS tamaño adaptable, panel, fullscreen, ventana estrecha y SHA-256 sin errores JS');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
