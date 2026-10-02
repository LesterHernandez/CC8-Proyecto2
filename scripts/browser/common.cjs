// Configuración de las pruebas opcionales. No se usa para ejecutar el visor.
const path = require('node:path');
const fs = require('node:fs');
const {chromium} = require(process.env.PRIB_PLAYWRIGHT || 'playwright');
const root = path.resolve(__dirname, '../..');
process.chdir(root);
fs.mkdirSync('build/browser-check', {recursive: true});
const baseUrl = (process.env.PRIB_URL || 'http://localhost:8080').replace(/\/$/, '');
const imageName = process.env.PRIB_IMAGE || 'imagen-17gb';
function launch() {
  // Chrome instalado; también se puede indicar un ejecutable de Chromium compatible.
  const options = process.env.PRIB_BROWSER_PATH
    ? {executablePath: process.env.PRIB_BROWSER_PATH} : {channel: 'chrome'};
  return chromium.launch({...options, headless: true});
}
module.exports = {launch, baseUrl, imageName};
