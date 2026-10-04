'use strict';
class BlockFailure extends Error {
  constructor(reason, message) { super(message); this.reason = reason; }
}
const PribDelta = {
  signature(rgb, width, height) {
    if (width < 1 || width > 128 || height < 1 || height > 128 || rgb.length !== width*height*3) throw new Error('RGB para firma inválido');
    const sums = Array.from({length:5},()=>[0,0,0]), counts = [0,0,0,0,0];
    for(let y=0;y<height;y++)for(let x=0;x<width;x++) {
      const q=1+Math.floor(y*2/height)*2+Math.floor(x*2/width), at=(y*width+x)*3;
      counts[0]++; counts[q]++;
      for(let c=0;c<3;c++){sums[0][c]+=rgb[at+c];sums[q][c]+=rgb[at+c];}
    }
    return sums.flatMap((sum,q)=>sum.map(v=>Math.floor(Math.floor(v/Math.max(1,counts[q]))/16).toString(16))).join('');
  },
  decode(base, payload) {
    if(payload.length<8 || payload.length>49152)throw new Error('DELTA truncado o excesivo');
    const data=new DataView(payload.buffer,payload.byteOffset,payload.byteLength);
    const length=data.getUint32(0),runs=data.getUint32(4);
    if(length!==base.length || !length || length>49152 || runs>length)throw new Error('Cabecera DELTA inválida');
    const rgb=base.slice();let position=8,previous=0;
    for(let run=0;run<runs;run++) {
      if(position+8>payload.length)throw new Error('Tramo DELTA truncado');
      const offset=data.getUint32(position),count=data.getUint32(position+4);position+=8;
      if(offset<previous || !count || offset+count>length || position+count>payload.length)throw new Error('Tramo DELTA fuera de límites o superpuesto');
      for(let i=0;i<count;i++)rgb[offset+i]^=payload[position+i];
      position+=count;previous=offset+count;
    }
    if(position!==payload.length)throw new Error('Sobran bytes DELTA');
    return rgb;
  },
  async reconstruct(header, payload, cache, digest) {
    let rgb;
    if(header.type==='BLOCK_FULL' && header.mode==='FULL' && header.codec==='RAW') {
      if(payload.length!==header.width*header.height*3)throw new Error('Longitud RGB inválida');
      rgb=payload;
    } else if(header.type==='BLOCK_REF' && ['REUSE','REF'].includes(header.mode) && header.codec==='CACHE' && !payload.length
          || header.type==='BLOCK_DELTA' && header.mode==='DELTA' && header.codec==='XOR_RUNS_1') {
      if(!/^[0-9a-f]{64}$/.test(header.baseHash))throw new Error('Hash de base inválido');
      const base=cache.get(header.baseImageId,header.baseId);
      if(!base)throw new BlockFailure('BASE_MISSING','Base ausente');
      if(base.header.expectedHash!==header.baseHash || base.header.width!==header.width || base.header.height!==header.height)
        throw new BlockFailure('CACHE_MISS','Declaración de caché incompatible');
      if(header.mode==='DELTA') {
        if(await digest(base.rgb)!==header.baseHash)throw new BlockFailure('HASH_MISMATCH','Base dañada');
        try {rgb=PribDelta.decode(base.rgb,payload);}
        catch(failure){throw new BlockFailure('DELTA_FAILED',failure.message);}
      } else {
        const exact=header.imageId===header.baseImageId && header.blockId===header.baseId;
        if((header.mode==='REUSE')!==exact)throw new Error('Modo de referencia inválido');
        rgb=base.rgb;
      }
    } else throw new Error('Modo de bloque no válido');
    if(await digest(rgb)!==header.expectedHash)throw new BlockFailure('HASH_MISMATCH','SHA-256 objetivo incorrecto');
    return rgb;
  }
};
if(typeof module!=='undefined')module.exports={PribDelta,BlockFailure};
