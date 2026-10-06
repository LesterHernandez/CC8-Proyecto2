// Ejecutar la dependencia como en un navegador HTTP, sin crypto de Node ni Web Crypto.
const assert = require('node:assert/strict');
const fs = require('node:fs'), vm = require('node:vm'), crypto = require('node:crypto');
const context = vm.createContext({window: {}, Uint8Array, ArrayBuffer});
vm.runInContext(fs.readFileSync('web/vendor/sha256.js', 'utf8'), context);
const hash = context.window.sha256;
assert.equal(hash(new Uint8Array()), 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
assert.equal(hash(new Uint8Array([97,98,99])), 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
// Incluir fronteras del padding y una vista con offset como los payloads PRIB.
for (const length of [1, 55, 56, 63, 64, 65, 49152]) {
  const storage = crypto.randomBytes(length + 19);
  const rgb = new Uint8Array(storage.buffer, storage.byteOffset + 7, length);
  assert.equal(hash(rgb), crypto.createHash('sha256').update(rgb).digest('hex'));
}
console.log('PASS SHA-256 JavaScript local: vectores, padding y bloques RGB con offset');
