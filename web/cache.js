'use strict';
// Caché de RGB verificado con presupuesto y utilidad; no conserva ArrayBuffers de red.
class BlockCache {
  constructor(send, view, maxBytes = 16 * 1024 * 1024, maxEntries = 1024) {
    this.send = send; this.view = view; this.maxBytes = maxBytes; this.maxEntries = maxEntries;
    this.entries = new Map(); this.holds = new Map(); this.hashes = new Map(); this.bytes = 0; this.seq = 0; this.tick = 0;
  }
  key(h) { return h.imageId + '/' + h.blockId; }
  report(operation, h = {}) {
    this.send('CACHE_STATE', {cacheSeq: ++this.seq, operation, imageId: h.imageId, blockId: h.blockId,
      width: h.width, height: h.height, hash: h.expectedHash, similarity: h.similarity});
  }
  lock(viewId) {
    if (this.holds.size >= 64) return false;
    this.holds.set(viewId, new Set(this.entries.keys())); return true;
  }
  release(viewId) { this.holds.delete(viewId); }
  pinned(key) { for (const keys of this.holds.values()) if (keys.has(key)) return true; return false; }
  get(imageId, blockId) { return this.entries.get(imageId + '/' + blockId); }
  value(entry, view = this.view()) {
    const h = entry.header;
    const recency = 1 / (1 + (this.tick-entry.used)/32);
    const frequency = Math.min(1, entry.hits/8);
    const proximity = view && view.imageId === h.imageId && view.level === h.level
      ? 1 / (1 + Math.hypot(h.x+h.width/2-view.x-view.width/2, h.y+h.height/2-view.y-view.height/2)/128) : 0;
    const aliases = this.hashes.get(h.expectedHash) || 0;
    // Igualdad exacta como potencial de REF; las bases DELTA pertenecen a etapa 6.
    return 3*recency + 3*proximity + 2*frequency + Math.min(2, aliases/4);
  }
  prepare(view) {
    // Reservar antes de anunciar/proteger la próxima vista permite renovar la caché.
    // Durante una generación activa, las bases antiguas conservan su protección.
    let missingBytes = 0, missingEntries = 0;
    const left = Math.floor(view.x), top = Math.floor(view.y);
    for (let row = Math.floor(top/128); row <= Math.floor((top+view.height-1)/128); row++) {
      for (let col = Math.floor(left/128); col <= Math.floor((left+view.width-1)/128); col++) {
        if (this.get(view.imageId, `${view.level}:${col}:${row}`)) continue;
        missingBytes += 128*128*3; missingEntries++;
      }
    }
    const reserveBytes = Math.min(this.maxBytes/2, missingBytes);
    const reserveEntries = Math.min(Math.floor(this.maxEntries/2), missingEntries);
    while (this.bytes > this.maxBytes-reserveBytes || this.entries.size > this.maxEntries-reserveEntries) {
      let victim, minimum = Infinity;
      for (const [key, entry] of this.entries) {
        const h = entry.header;
        if (this.pinned(key) || h.imageId === view.imageId && h.level === view.level
            && h.x < left+view.width && h.y < top+view.height && h.x+h.width > left && h.y+h.height > top) continue;
        const score = this.value(entry, view);
        if (score < minimum) { victim = key; minimum = score; }
      }
      if (!victim || !this.drop(victim)) break;
    }
  }
  drop(key, force = false) {
    const item = this.entries.get(key); if (!item || !force && this.pinned(key)) return false;
    this.entries.delete(key); this.bytes -= item.rgb.length;
    const hash = item.header.expectedHash, count = this.hashes.get(hash) - 1;
    if (count) this.hashes.set(hash, count); else this.hashes.delete(hash);
    for (const keys of this.holds.values()) keys.delete(key);
    this.report('DROP', item.header); return true;
  }
  invalidate(imageId, blockId, all = false) {
    // La integridad puede invalidar una base protegida; sus dependencias recuperan por FULL.
    if (all) {
      this.entries.clear(); this.hashes.clear(); this.bytes = 0;
      for (const keys of this.holds.values()) keys.clear();
      this.report('CLEAR');
    } else this.drop(imageId + '/' + blockId, true);
  }
  put(header, rgb) {
    const key = this.key(header), old = this.entries.get(key); this.tick++;
    if (old) { old.used = this.tick; old.hits++; return true; }
    const item = {header: {...header}, rgb, used: this.tick, hits: 1};
    if (typeof PribDelta !== 'undefined') item.header.similarity = PribDelta.signature(rgb, header.width, header.height);
    // Si todas las entradas están protegidas, dibujar el bloque sin conservarlo es válido.
    while (this.bytes + rgb.length > this.maxBytes || this.entries.size >= this.maxEntries) {
      let victim, minimum = Infinity;
      for (const [candidate, entry] of this.entries) {
        if (this.pinned(candidate)) continue;
        const score = this.value(entry);
        if (score < minimum) { victim = candidate; minimum = score; }
      }
      if (!victim || this.value(item) < minimum || !this.drop(victim)) return false;
    }
    this.entries.set(key, item); this.bytes += rgb.length;
    this.hashes.set(header.expectedHash, (this.hashes.get(header.expectedHash) || 0) + 1);
    for (const keys of this.holds.values()) keys.add(key);
    this.report('PUT', item.header); return true;
  }
  reset() { this.entries.clear(); this.holds.clear(); this.hashes.clear(); this.bytes = this.seq = this.tick = 0; }
}
if (typeof module !== 'undefined') module.exports = BlockCache;
