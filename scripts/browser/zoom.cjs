// Prueba de desarrollo: requiere servidor activo e imagen de 17 GB preparada.
const assert=require('node:assert/strict');
const {launch, baseUrl, imageName} = require('./common.cjs');
(async()=>{
 const browser=await launch();
 try {
 const p=await browser.newPage({viewport:{width:1440,height:1000}});
 const errors=[],views=[];p.on('pageerror',e=>errors.push(e.message));
 p.on('websocket',ws=>ws.on('framesent',f=>{try{const m=JSON.parse(f.payload);if(m.type==='VIEW')views.push(m);}catch{}}));
 await p.goto(baseUrl);
 const settle=async()=>{await p.waitForTimeout(350);await p.waitForFunction(()=>document.querySelector('#view-state').textContent==='Vista completa');};
 await settle();await p.selectOption('#image',{label:imageName});await settle();
 await p.selectOption('#level','0');await settle();
 await p.fill('#x','10000');await p.fill('#y','10000');await p.click('#go');await settle();
 const before=views.at(-1);
 await p.click('#zoom-in');await settle();
 const after=views.at(-1);assert(after.width<before.width);assert(after.height<before.height);
 assert.equal(await p.locator('#zoom-value').textContent(),'200 %');
 // El punto bajo el cursor debe mantenerse al cambiar escala.
 const box=await p.locator('#canvas').boundingBox();
 const anchor=await p.evaluate(()=>({x:x+300/magnification,y:y+200/magnification}));
 await p.mouse.move(box.x+300,box.y+200);await p.mouse.wheel(0,-100);await settle();
 const anchored=await p.evaluate(()=>({x:x+300/magnification,y:y+200/magnification}));
 assert(Math.abs(anchor.x-anchored.x)<0.51);assert(Math.abs(anchor.y-anchored.y)<0.51);
 // Aumento 4x: comprobar que se dibujó contenido y que el suavizado sigue desactivado.
 assert(await p.evaluate(()=>{
  const tile=tileContext.getImageData(0,0,tileCanvas.width,tileCanvas.height);
  return tile.data.some(v=>v!==0) && context.imageSmoothingEnabled===false && magnification===4;
 }));
 await p.screenshot({path:'build/browser-check/zoom-400.png'});
 const origin=await p.evaluate(()=>({x,y}));
 await p.mouse.down();await p.mouse.move(box.x+340,box.y+240,{steps:4});await p.mouse.up();await settle();
 const moved=await p.evaluate(()=>({x,y}));assert.equal(origin.x-moved.x,10);assert.equal(origin.y-moved.y,10);
 // Un acercamiento extremo sigue pidiendo solo los píxeles visibles.
 for(let i=0;i<16;i++)await p.click('#zoom-in');
 await settle();assert(views.at(-1).width<=2);assert(views.at(-1).height<=2);
 assert.equal(await p.locator('#error').isVisible(),false);
 await p.click('#fit');await settle();
 assert.equal(await p.evaluate(()=>magnification),1);
 await p.selectOption('#level','0');await settle();
 await p.click('#zoom-in');await settle();
 for(const id of ['x','y'])await p.fill('#'+id,await p.locator('#'+id).getAttribute('max'));
 await p.click('#go');await settle();
 const edge=views.at(-1);assert(edge.x+edge.width<=75471);assert(edge.y+edge.height<=75471);
 assert.equal(await p.locator('#integrity').textContent(),'SHA-256 correcto');
 assert.deepEqual(errors,[]);
 console.log('PASS zoom 2x/4x/extremo, región reducida, anclaje, arrastre proporcional, bordes y volver a imagen completa');
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
