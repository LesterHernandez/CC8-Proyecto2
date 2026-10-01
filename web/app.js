'use strict';
// El visor conserva solo el Canvas de la vista actual. No carga una imagen gigante en RAM.
const $ = id => document.getElementById(id);
const canvas = $('canvas'), context = canvas.getContext('2d');
let socket, sessionId = '', images = [], current, level = 0, x = 0, y = 0, viewId = 0;
let expected = 0, verified = 0, receivedBytes = 0, seen = new Set(), epoch = 0;
let timer, drag, resizeTimer, activeView, magnification = 1;
// Solo un bloque auxiliar para ampliar sin suavizado; no es una caché de imagen.
const tileCanvas = document.createElement('canvas'), tileContext = tileCanvas.getContext('2d');
const MAX_ZOOM = 65536; // Evita desbordamientos; permite inspeccionar mucho menos de un píxel.
const decoder = new TextDecoder('utf-8', {fatal: true});

function error(message) { $('error').textContent = message; $('error').hidden = false; }
function send(type, fields = {}) {
  if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({version: 1, type, sessionId, ...fields}));
}
function dimensions() {
  let width = current.width, height = current.height;
  for (let i = 0; i < level; i++) { width = Math.ceil(width / 2); height = Math.ceil(height / 2); }
  return {width, height};
}
function counters() {
  $('blocks').textContent = `${verified} / ${expected}`;
  $('bytes').textContent = `${(receivedBytes / 1024).toFixed(1)} KB`;
  $('integrity').textContent = verified ? 'SHA-256 correcto' : 'Pendiente';
}
// El Canvas mantiene el tamaño de pantalla; la región solicitada disminuye al acercar.
function viewport() {
  return {width: Math.min(3840, Math.max(1, Math.floor($('stage').clientWidth))),
          height: Math.min(2160, Math.max(1, Math.floor($('stage').clientHeight)))};
}
function requestView() {
  if (!current || !sessionId || socket?.readyState !== WebSocket.OPEN) return;
  const size = dimensions();
  canvas.width = viewport().width;
  canvas.height = viewport().height;
  // El origen puede ser fraccionario para mantener el punto bajo el cursor.
  const visibleWidth = canvas.width / magnification, visibleHeight = canvas.height / magnification;
  x = Math.max(0, Math.min(x, Math.max(0, size.width - visibleWidth)));
  y = Math.max(0, Math.min(y, Math.max(0, size.height - visibleHeight)));
  if (magnification === 1) { x = Math.round(x); y = Math.round(y); }
  const left = Math.floor(x), top = Math.floor(y);
  const width = Math.min(size.width, Math.ceil(x + visibleWidth)) - left;
  const height = Math.min(size.height, Math.ceil(y + visibleHeight)) - top;
  $('x').value = left; $('y').value = top;
  $('x').max = Math.floor(Math.max(0, size.width-visibleWidth));
  $('y').max = Math.floor(Math.max(0, size.height-visibleHeight));
  $('zoom-value').textContent = (100 * magnification / 2 ** level).toLocaleString(undefined, {maximumFractionDigits: 2}) + ' %';
  viewId++; activeView = {imageId: current.imageId, level, x, y, width, height, magnification}; expected = 0; verified = 0; receivedBytes = 0; seen.clear(); counters();
  context.fillStyle = '#dfe7ec'; context.fillRect(0, 0, canvas.width, canvas.height);
  $('view-id').textContent = viewId; $('view-state').textContent = 'Solicitando bloques…';
  $('dimensions').textContent = `${size.width.toLocaleString()} × ${size.height.toLocaleString()} · nivel ${level}`;
  send('VIEW', {imageId: current.imageId, viewId, level, x: left, y: top, width, height});
}
function scheduleView() { clearTimeout(timer); timer = setTimeout(requestView, 100); }
function selectImage() {
  current = images.find(item => item.imageId === $('image').value);
  $('level').replaceChildren();
  for (let i = 0; i < current.levels; i++) $('level').add(new Option(i === 0 ? '0 · Resolución original' : `${i} · Reducida`, i));
  x = 0; y = 0; fit();
}
function fit() {
  if (!current) return;
  level = 0; magnification = 1;
  while (level + 1 < current.levels && (dimensions().width > viewport().width || dimensions().height > viewport().height)) level++;
  $('level').value = level; x = 0; y = 0; clearTimeout(timer); requestView();
}
// El mismo punto de la imagen queda bajo el cursor (o bajo el centro con los botones).
function setZoom(nextLevel, nextMagnification, px = canvas.width/2, py = canvas.height/2) {
  if (!current || !sessionId || socket?.readyState !== WebSocket.OPEN) return;
  const ratio = 2 ** (level - nextLevel);
  x = (x + px/magnification) * ratio - px/nextMagnification;
  y = (y + py/magnification) * ratio - py/nextMagnification;
  level = nextLevel; magnification = nextMagnification;
  $('level').value = level; scheduleView();
}
function changeLevel(next) {
  setZoom(Math.max(0, Math.min(current.levels-1, next)), 1);
}
function zoom(direction, px, py) {
  if (!current) return;
  // Primero usamos el detalle real de la pirámide. Después ampliamos el nivel original.
  if (direction > 0) {
    if (level > 0) setZoom(level-1, 1, px, py);
    else setZoom(0, Math.min(MAX_ZOOM, magnification*2), px, py);
  } else {
    if (magnification > 1) setZoom(0, Math.max(1, magnification/2), px, py);
    else setZoom(Math.min(current.levels-1, level+1), 1, px, py);
  }
}

async function receive(data, generation) {
  if (generation !== epoch) return;
  if (typeof data === 'string') {
    const message = JSON.parse(data);
    if (message.version !== 1) throw new Error('Versión PRIB no compatible');
    if (message.type === 'ERROR') throw new Error(message.message);
    if (message.type === 'IMAGE_INFO') {
      sessionId = message.sessionId; images = message.images.sort((a,b) => a.width*a.height - b.width*b.height);
      $('session').textContent = sessionId; $('image').replaceChildren();
      images.forEach(item => $('image').add(new Option(item.name, item.imageId)));
      for (const id of ['image', 'level', 'fit', 'go', 'zoom-in', 'zoom-out']) $(id).disabled = false;
      $('connection').textContent = 'Conectado'; selectImage(); return;
    }
    if (message.sessionId !== sessionId) throw new Error('La respuesta pertenece a otra sesión');
    if (message.viewId !== viewId) return;
    if (message.type === 'VIEW_ACCEPTED') { expected = message.blocks; counters(); }
    else if (message.type === 'VIEW_DONE') {
      if (verified !== message.blocks || verified !== expected) throw new Error('La vista terminó con bloques faltantes');
      $('view-state').textContent = 'Vista completa';
    }
    return;
  }
  // Cabecera binaria PRIB: longitud uint32 big-endian, JSON UTF-8 y píxeles RGB.
  if (data.byteLength < 4) throw new Error('Bloque truncado');
  const headerLength = new DataView(data).getUint32(0);
  if (headerLength > 4096 || headerLength + 4 > data.byteLength) throw new Error('Cabecera de bloque inválida');
  const header = JSON.parse(decoder.decode(new Uint8Array(data, 4, headerLength)));
  if (header.sessionId !== sessionId || header.version !== 1) throw new Error('Sesión o versión de bloque inválida');
  // Los datos que ya viajaban al cambiar la vista no se dibujan en la vista nueva.
  if (header.viewId !== viewId) return;
  const rgb = new Uint8Array(data, 4 + headerLength);
  if (header.type !== 'BLOCK_FULL' || header.imageId !== activeView.imageId || header.level !== activeView.level
      || header.codec !== 'RAW' || header.format !== 'RGB8' || header.width < 1 || header.width > 128
      || header.height < 1 || header.height > 128 || rgb.length !== header.width*header.height*3
      || rgb.length !== header.payloadLength || seen.has(header.blockId)) throw new Error('Bloque no válido');
  const hash = Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', rgb)), byte => byte.toString(16).padStart(2,'0')).join('');
  if (generation !== epoch || header.viewId !== viewId) return;
  if (hash !== header.expectedHash) throw new Error('SHA-256 incorrecto: se detuvo la vista');
  const pixels = new ImageData(header.width, header.height);
  for (let i = 0, j = 0; i < rgb.length; i += 3, j += 4) {
    pixels.data[j] = rgb[i]; pixels.data[j+1] = rgb[i+1]; pixels.data[j+2] = rgb[i+2]; pixels.data[j+3] = 255;
  }
  // La vista enviada es inmutable aunque el usuario ya esté arrastrando hacia otra zona.
  tileCanvas.width = header.width; tileCanvas.height = header.height;
  tileContext.putImageData(pixels, 0, 0);
  context.imageSmoothingEnabled = false; // Mostrar los píxeles originales, sin inventar detalles.
  const scale = activeView.magnification;
  context.drawImage(tileCanvas, (header.x-activeView.x)*scale, (header.y-activeView.y)*scale,
                    header.width*scale, header.height*scale);
  seen.add(header.blockId); verified++; receivedBytes += rgb.length; counters();
  $('view-state').textContent = 'Verificando bloques…';
  send('ACK', {viewId, transferId: header.transferId, hash});
}

function connect() {
  const generation = ++epoch;
  socket?.close(); clearTimeout(timer); sessionId = ''; current = null; viewId = 0;
  $('error').hidden = true; $('connection').textContent = 'Conectando…';
  $('view-state').textContent = 'Esperando sesión'; $('session').textContent = '—'; $('view-id').textContent = '—';
  expected = 0; verified = 0; receivedBytes = 0; seen.clear(); counters();
  context.clearRect(0, 0, canvas.width, canvas.height);
  for (const id of ['image', 'level', 'fit', 'go', 'zoom-in', 'zoom-out']) $(id).disabled = true;
  if (!crypto.subtle) { error('Abre el visor en http://localhost para verificar SHA-256.'); return; }
  const ws = new WebSocket(`ws://${location.host}/ws`); socket = ws; ws.binaryType = 'arraybuffer';
  let chain = Promise.resolve(), queuedBytes = 0, failed = false;
  ws.onopen = () => { if (generation === epoch) send('HELLO'); };
  ws.onmessage = event => {
    // Serializar la verificación conserva el orden aunque SHA-256 sea asíncrono.
    const size = typeof event.data === 'string' ? event.data.length * 2 : event.data.byteLength;
    queuedBytes += size;
    if (queuedBytes > 32 * 1024 * 1024) { failed = true; error('Capacidad de procesamiento excedida'); ws.close(); return; }
    chain = chain.then(() => { if (!failed) return receive(event.data, generation); }).catch(failure => {
      failed = true; // No dibujar más bloques de una conexión cuya integridad falló.
      if (generation === epoch) { error(failure.message); ws.close(); }
    }).finally(() => { queuedBytes -= size; });
  };
  ws.onclose = () => {
    if (generation !== epoch) return;
    $('connection').textContent = 'Desconectado'; $('view-state').textContent = 'Conexión cerrada';
    for (const id of ['image', 'level', 'fit', 'go', 'zoom-in', 'zoom-out']) $(id).disabled = true;
  };
  ws.onerror = () => { if (generation === epoch) error('No se pudo conectar con el servidor local.'); };
}
$('image').onchange = selectImage;
$('level').onchange = () => changeLevel(Number($('level').value));
$('fit').onclick = fit; $('reconnect').onclick = connect;
$('position').onsubmit = event => {
  event.preventDefault(); x = Number($('x').value); y = Number($('y').value);
  if (Number.isSafeInteger(x) && Number.isSafeInteger(y)) { clearTimeout(timer); requestView(); }
};
canvas.onpointerdown = event => { drag = {x: event.clientX, y: event.clientY, originX: x, originY: y, scale: magnification}; canvas.setPointerCapture(event.pointerId); };
canvas.onpointermove = event => {
  if (!drag) return;
  x = drag.originX - (event.clientX - drag.x)/drag.scale; y = drag.originY - (event.clientY - drag.y)/drag.scale; scheduleView();
};
canvas.onpointerup = canvas.onpointercancel = () => { if (drag) { drag = null; clearTimeout(timer); requestView(); } };
$('zoom-in').onclick = () => zoom(1);
$('zoom-out').onclick = () => zoom(-1);
canvas.addEventListener('wheel', event => {
  event.preventDefault();
  const box = canvas.getBoundingClientRect();
  if (event.deltaY) zoom(event.deltaY < 0 ? 1 : -1, event.clientX-box.left, event.clientY-box.top);
}, {passive: false});
// Observar el área real incluye cambios de ventana, panel y pantalla completa.
new ResizeObserver(() => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => {
    const size = viewport();
    if (size.width !== canvas.width || size.height !== canvas.height) requestView();
  }, 180);
}).observe($('stage'));
$('toggle-info').onclick = () => {
  const expanded = $('transfer').hidden;
  $('transfer').hidden = !expanded;
  document.querySelector('.layout').classList.toggle('with-info', expanded);
  $('toggle-info').setAttribute('aria-expanded', String(expanded));
  $('toggle-info').textContent = expanded ? 'Ocultar transferencia' : 'Mostrar transferencia';
};
$('fullscreen').onclick = async () => {
  try {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await document.documentElement.requestFullscreen();
  } catch { error('No se pudo activar la pantalla completa en este navegador.'); }
};
document.addEventListener('fullscreenchange', () => {
  $('fullscreen').textContent = document.fullscreenElement ? 'Salir de pantalla completa' : 'Pantalla completa';
});
connect();
