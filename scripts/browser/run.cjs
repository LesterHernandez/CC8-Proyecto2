// Ejecutar en serie: un fallo detiene la suite y devuelve código distinto de cero.
const {spawnSync} = require('node:child_process');
const path = require('node:path');
for (const test of ['sessions', 'zoom', 'responsive', 'credits']) {
  const result = spawnSync(process.execPath, [path.join(__dirname, test + '.cjs')], {stdio: 'inherit'});
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status || 1);
}
