// Reloj controlado: no depende de la rapidez del equipo ni de temporizadores reales.
const assert = require('node:assert/strict');
const {ViewMetrics} = require('../web/metrics.js');
let now = 0;
const metric = new ViewMetrics(100, false, () => now);
now = 10; metric.block('FULL', 200);
now = 20; metric.creditState(true); metric.grant();
now = 40; metric.creditState(false); metric.block('DELTA', 300);
now = 50; metric.finish();
const expected = {elapsedMs:50, firstBlockMs:10, waitCreditMs:20,
  cacheAverageBytes:200, cacheHitRate:0.5, grants:1};
assert.deepEqual(metric.snapshot(), expected);
// Controles tardíos no deben cambiar el resultado de una vista terminada.
now = 100; metric.grant(); metric.creditState(true); metric.block('REF', 999);
assert.deepEqual(metric.snapshot(), expected);
const empty = new ViewMetrics(25, true, () => now);
assert.equal(empty.snapshot().firstBlockMs, null);
now = 130; empty.finish();
assert.equal(empty.snapshot().waitCreditMs, 30);
assert.equal(empty.snapshot().cacheAverageBytes, 25);
assert.equal(empty.snapshot().cacheHitRate, 0);
console.log('PASS métricas: primer bloque, espera, promedio temporal, caché y cierre de vista');
