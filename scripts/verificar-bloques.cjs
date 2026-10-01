// Verifica que JavaScript interpreta el contrato RGB y SHA-256 de Java.
// No utiliza dependencias externas. La prueba del navegador vendrá con la interfaz.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const folder = process.argv[2];
if (!folder) throw new Error('Uso: node scripts/verificar-bloques.cjs output/prueba-782');
// El CSV describe geometría, longitud y hash; la primera línea es la cabecera.
const rows = fs.readFileSync(path.join(folder, 'blocks.csv'), 'utf8').trim().split(/\r?\n/).slice(1);
for (const row of rows) {
    const [id, , , width, height, size, hash] = row.split(',');
    const rgb = fs.readFileSync(path.join(folder, id + '.rgb'));
    // RGB usa tres bytes por píxel, también en los bloques parciales del borde.
    if (rgb.length !== Number(width)*Number(height)*3 || rgb.length !== Number(size))
        throw new Error('Longitud incorrecta: ' + id);
    // El hash se calcula sobre los bytes RGB exactos, antes de agregar alfa.
    if (crypto.createHash('sha256').update(rgb).digest('hex') !== hash)
        throw new Error('Hash incorrecto: ' + id);
    // Simula la conversión necesaria para Canvas y comprueba que RGB no cambia.
    const rgba = new Uint8ClampedArray(Number(width)*Number(height)*4);
    // i recorre RGB de tres en tres; j recorre RGBA de cuatro en cuatro.
    for (let i=0, j=0; i<rgb.length; i+=3, j+=4) {
        rgba[j]=rgb[i]; rgba[j+1]=rgb[i+1]; rgba[j+2]=rgb[i+2]; rgba[j+3]=255;
        if (rgba[j]!==rgb[i] || rgba[j+1]!==rgb[i+1] || rgba[j+2]!==rgb[i+2])
            throw new Error('Conversión incorrecta');
    }
}
console.log('PASS JavaScript: ' + rows.length + ' bloques, longitudes, hashes y conversión RGB/RGBA');
