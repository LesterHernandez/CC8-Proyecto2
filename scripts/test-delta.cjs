// Vectores de Java y fallos semánticos reales del reconstructor del navegador.
const assert=require('node:assert/strict'),fs=require('node:fs'),crypto=require('node:crypto');
const {PribDelta,BlockFailure}=require('../web/delta.js');
const digest=async rgb=>crypto.createHash('sha256').update(rgb).digest('hex');
(async()=>{
 const vectors=JSON.parse(fs.readFileSync('build/delta-vectors.json','utf8'));
 for(const v of vectors) {
  const base=Uint8Array.from(Buffer.from(v.base,'hex')),target=Uint8Array.from(Buffer.from(v.target,'hex')),payload=Uint8Array.from(Buffer.from(v.payload,'hex'));
  assert.deepEqual(PribDelta.decode(base,payload),target);assert.equal(PribDelta.signature(target,v.width,v.height),v.similarity);
  assert.throws(()=>PribDelta.decode(base,payload.slice(0,-1)));assert.throws(()=>PribDelta.decode(base,Uint8Array.from([...payload,0])));
  const header={type:'BLOCK_DELTA',mode:'DELTA',codec:'XOR_RUNS_1',width:v.width,height:v.height,baseImageId:'i',baseId:'b',baseHash:await digest(base),expectedHash:await digest(target)};
  const good={header:{width:v.width,height:v.height,expectedHash:header.baseHash},rgb:base};
  assert.deepEqual(await PribDelta.reconstruct(header,payload,{get:()=>good},digest),target);
  const fail=reason=>e=>e instanceof BlockFailure&&e.reason===reason;
  await assert.rejects(()=>PribDelta.reconstruct(header,payload,{get:()=>undefined},digest),fail('BASE_MISSING'));
  await assert.rejects(()=>PribDelta.reconstruct(header,payload,{get:()=>({...good,header:{...good.header,expectedHash:'b'.repeat(64)}})},digest),fail('CACHE_MISS'));
  const bad=base.slice();bad[0]^=1;
  await assert.rejects(()=>PribDelta.reconstruct(header,payload,{get:()=>({...good,rgb:bad})},digest),fail('HASH_MISMATCH'));
  await assert.rejects(()=>PribDelta.reconstruct(header,payload.slice(0,-1),{get:()=>good},digest),fail('DELTA_FAILED'));
  await assert.rejects(()=>PribDelta.reconstruct({...header,expectedHash:'0'.repeat(64)},payload,{get:()=>good},digest),fail('HASH_MISMATCH'));
 }
 console.log('PASS Java/JS: RGB y firmas idénticos, DELTA inválido, base ausente, caché desajustada y hashes incorrectos');
})().catch(e=>{console.error(e);process.exitCode=1;});
