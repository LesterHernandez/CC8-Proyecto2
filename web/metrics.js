'use strict';
// Mediciones locales de una vista. No alteran el protocolo ni retienen bloques.
class ViewMetrics {
  constructor(cacheBytes, waiting = false, clock = () => performance.now()) {
    this.clock = clock;
    this.start = this.last = clock();
    this.cacheBytes = cacheBytes;
    this.cacheArea = this.waitMs = this.blocks = this.hits = this.grants = 0;
    this.waiting = waiting;
    this.firstBlockMs = null;
    this.end = null;
  }
  sample(cacheBytes = this.cacheBytes) {
    if (this.end !== null) return;
    const now = this.clock(), elapsed = now - this.last;
    // Promedio ponderado por tiempo, no por cantidad de bloques recibidos.
    this.cacheArea += this.cacheBytes * elapsed;
    if (this.waiting) this.waitMs += elapsed;
    this.last = now;
    this.cacheBytes = cacheBytes;
  }
  creditState(waiting) {
    this.sample();
    this.waiting = waiting;
  }
  grant() { if (this.end === null) this.grants++; }
  block(mode, cacheBytes) {
    if (this.end !== null) return;
    this.sample(cacheBytes);
    if (this.firstBlockMs === null) this.firstBlockMs = this.last - this.start;
    this.blocks++;
    if (mode !== 'FULL') this.hits++; // REUSE, REF y DELTA usan RGB ya retenido.
  }
  finish() { this.sample(); this.end ??= this.last; }
  snapshot() {
    this.sample();
    const elapsedMs = (this.end ?? this.last) - this.start;
    return {elapsedMs, firstBlockMs: this.firstBlockMs, waitCreditMs: this.waitMs,
      cacheAverageBytes: elapsedMs ? this.cacheArea / elapsedMs : this.cacheBytes,
      cacheHitRate: this.blocks ? this.hits / this.blocks : 0, grants: this.grants};
  }
}
if (typeof module !== 'undefined') module.exports = {ViewMetrics};
