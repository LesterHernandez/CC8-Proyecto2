// Prueba de política de caché del navegador sin DOM ni paquetes externos.
const assert = require('node:assert/strict');
const BlockCache = require('../web/cache.js');
const reports = [], rgb = new Uint8Array(128*128*3);
const header = id => ({imageId:'i', blockId:`0:${id}:0`, level:0, x:id*128,y:0,width:128,height:128,expectedHash:'a'.repeat(64)});
const view = {imageId:'i',level:0,x:0,y:0,width:256,height:128};
const cache = new BlockCache((type, fields)=>reports.push(fields),()=>view,rgb.length*2,2);
assert(cache.put(header(0),rgb)); assert(cache.put(header(1),rgb));
assert(cache.lock(1)); assert(!cache.drop('i/0:0:0'));
assert(!cache.put(header(2),rgb)); assert.equal(cache.bytes,rgb.length*2);
// La segunda generación sigue protegiendo bases aunque llegue el cierre de la primera.
assert(cache.lock(2)); cache.release(1); assert(!cache.drop('i/0:0:0'));
cache.release(2); assert(cache.drop('i/0:1:0')); assert(cache.put(header(2),rgb));
assert(cache.lock(3)); assert(cache.get('i','0:2:0')); cache.release(3);
// La utilidad incluye frecuencia y proximidad, además de recencia.
for(let i=0;i<8;i++)cache.put(header(0),rgb);
view.x=0; assert(cache.value(cache.get('i','0:0:0'))>cache.value(cache.get('i','0:2:0')));
assert(reports.some(r=>r.operation==='DROP'));
assert(reports.every((r,i)=>r.cacheSeq===i+1));
cache.reset(); assert.equal(cache.bytes,0); assert.equal(cache.holds.size,0); assert.equal(cache.hashes.size,0);
for(let i=0;i<64;i++)assert(cache.lock(i)); assert(!cache.lock(65));
const rotating = new BlockCache(()=>{},()=>view,rgb.length*2,2);
rotating.put(header(0),rgb); rotating.put(header(1),rgb); rotating.lock(1);
rotating.prepare({imageId:'i',level:0,x:512,y:0,width:128,height:128});
assert.equal(rotating.entries.size,2); // Las bases en tránsito siguen protegidas.
rotating.release(1);
rotating.prepare({imageId:'i',level:0,x:512,y:0,width:128,height:128});
assert.equal(rotating.entries.size,1); assert(rotating.lock(2));
assert(rotating.put(header(4),rgb)); assert(rotating.get('i','0:4:0'));
rotating.release(2);
rotating.prepare({imageId:'i',level:0,x:512,y:0,width:128,height:128});
assert(rotating.get('i','0:4:0')); // No expulsar el objetivo de REUSE.
const invalid = new BlockCache((type,fields)=>reports.push(fields),()=>view,rgb.length*2,2);
invalid.put(header(0),rgb); invalid.lock(1); invalid.invalidate('i','0:0:0');
assert.equal(invalid.bytes,0);assert.equal(invalid.get('i','0:0:0'),undefined);assert.equal(invalid.holds.size,1);
invalid.put(header(1),rgb);invalid.invalidate('i','unused',true);
assert.equal(invalid.bytes,0);assert.equal(invalid.entries.size,0);assert.equal(invalid.hashes.size,0);
assert.equal(invalid.holds.size,1);assert.equal(invalid.holds.get(1).size,0);invalid.release(1);
console.log('PASS caché navegador: presupuesto, bases protegidas, generaciones, utilidad, inventario y reinicio');
