'use strict';
// Canvas de la vista actual y caché RGB acotada; nunca carga la imagen completa.
const $ = id => document.getElementById(id);
const canvas = $('canvas'), context = canvas.getContext('2d');
let socket, sessionId = '', images = [], current, level = 0, x = 0, y = 0, viewId = 0;
let expected = 0, verified = 0, receivedBytes = 0, seen = new Set(), epoch = 0;
let timer, drag, resizeTimer, activeView, magnification = 1;
// Solo un bloque auxiliar para ampliar sin suavizado; no es una caché de imagen.
const tileCanvas = document.createElement('canvas'), tileContext = tileCanvas.getContext('2d');
const MAX_ZOOM = 65536; // Evita desbordamientos; permite inspeccionar mucho menos de un píxel.
const CREDIT_CAPACITY = 256 * 1024;
const cache = new BlockCache(send, () => activeView);
let modes = {FULL: 0, REUSE: 0, REF: 0, DELTA: 0}, pribBytes = 0, savedBytes = 0;
let recoveries = 0, failures = new Map();
let releasedBytes = 0, lastGrantedBytes = 0, grantId = 0, grantsPaused = false, resumeView;
// Solo se conserva el total liberado; pausar concesiones no retiene bloques en RAM.
function grantCapacity(force = false) {
  if (!grantsPaused && sessionId && socket?.readyState === WebSocket.OPEN
      && (force || releasedBytes - lastGrantedBytes >= 16 * 1024)) {
    send('CREDIT_GRANT', {grantId: ++grantId, releasedBytes}); lastGrantedBytes = releasedBytes;
  }
}
const decoder = new TextDecoder('utf-8', {fatal: true});

// Actualizaciones pequeñas de interfaz compartidas por conexión y recepción.
function setControlsEnabled(enabled) {
  for (const id of ['image', 'level', 'fit', 'go', 'zoom-in', 'zoom-out']) $(id).disabled = !enabled;
}
function resetCounters() {
  expected = 0; verified = 0; receivedBytes = 0; pribBytes = savedBytes = recoveries = 0; failures.clear();
  modes = {FULL: 0, REUSE: 0, REF: 0, DELTA: 0}; seen.clear(); counters();
}
function showCreditStatus(message) {
  $('credit-available').textContent = (message.availableBytes/1024).toFixed(1) + ' KiB';
  $('credit-outstanding').textContent = (message.outstandingBytes/1024).toFixed(1) + ' KiB';
  $('credit-state').textContent = {WAIT_INIT: 'Esperando capacidad inicial', WAIT_CREDIT: 'Esperando crédito',
    SENDING: 'Enviando', IDLE: 'Sin envíos pendientes'}[message.state] || message.state;
}
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
  $('bytes').textContent = receivedBytes < 1024 ? `${receivedBytes} B` : `${(receivedBytes / 1024).toFixed(1)} KiB`;
  $('integrity').textContent = verified ? 'SHA-256 correcto' : 'Pendiente';
  $('modes').textContent = `${modes.FULL} / ${modes.REUSE} / ${modes.REF} / ${modes.DELTA}`;
  $('prib-bytes').textContent = (pribBytes/1024).toFixed(1) + ' KiB';
  $('recoveries').textContent = recoveries;
  $('saved-bytes').textContent = (savedBytes/1024).toFixed(1) + ' KiB';
  $('cache-bytes').textContent = (cache.bytes/1024/1024).toFixed(2) + ' / 16 MiB';
}
// El Canvas mantiene el tamaño de pantalla; la región solicitada disminuye al acercar.
function viewport() {
  return {width: Math.min(3840, Math.max(1, Math.floor($('stage').clientWidth))),
          height: Math.min(2160, Math.max(1, Math.floor($('stage').clientHeight)))};
}
function requestView() {
  if (!current || !sessionId || socket?.readyState !== WebSocket.OPEN) return;
  const size = dimensions(), viewportSize = viewport();
  // El origen puede ser fraccionario para mantener el punto bajo el cursor.
  const visibleWidth = viewportSize.width / magnification, visibleHeight = viewportSize.height / magnification;
  x = Math.max(0, Math.min(x, Math.max(0, size.width - visibleWidth)));
  y = Math.max(0, Math.min(y, Math.max(0, size.height - visibleHeight)));
  if (magnification === 1) { x = Math.round(x); y = Math.round(y); }
  const left = Math.floor(x), top = Math.floor(y);
  const width = Math.min(size.width, Math.ceil(x + visibleWidth)) - left;
  const height = Math.min(size.height, Math.ceil(y + visibleHeight)) - top;
  const nextView = {imageId: current.imageId, level, x, y, width, height, magnification};
  cache.prepare(nextView);
  if (!cache.lock(viewId + 1)) { scheduleView(); return; }
  if (viewId) send('CANCEL', {viewId});
  canvas.width = viewportSize.width; canvas.height = viewportSize.height;
  $('x').value = left; $('y').value = top;
  $('x').max = Math.floor(Math.max(0, size.width-visibleWidth));
  $('y').max = Math.floor(Math.max(0, size.height-visibleHeight));
  $('zoom-value').textContent = (100 * magnification / 2 ** level).toLocaleString(undefined, {maximumFractionDigits: 2}) + ' %';
  viewId++; activeView = nextView; resetCounters();
  context.fillStyle = '#dfe7ec'; context.fillRect(0, 0, canvas.width, canvas.height);
  $('view-id').textContent = viewId; $('view-state').textContent = 'Solicitando bloques…';
  $('dimensions').textContent = `${size.width.toLocaleString()} × ${size.height.toLocaleString()} · nivel ${level}`;
  send('VIEW', {imageId: current.imageId, viewId, level, x: left, y: top, width, height});
}
function scheduleView() { clearTimeout(timer); timer = setTimeout(requestView, 100); }
function selectImage(fitView = true) {
  current = images.find(item => item.imageId === $('image').value);
  $('level').replaceChildren();
  for (let i = 0; i < current.levels; i++) $('level').add(new Option(i === 0 ? '0 · Resolución original' : `${i} · Reducida`, i));
  x = 0; y = 0; if (fitView) fit();
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

// Dibuja únicamente datos que ya superaron la verificación de integridad.
function drawBlock(header, rgb, view) {
  const pixels = new ImageData(header.width, header.height);
  for (let i = 0, j = 0; i < rgb.length; i += 3, j += 4) {
    pixels.data[j] = rgb[i]; pixels.data[j+1] = rgb[i+1]; pixels.data[j+2] = rgb[i+2]; pixels.data[j+3] = 255;
  }
  // La vista enviada es inmutable aunque el usuario ya esté arrastrando hacia otra zona.
  tileCanvas.width = header.width; tileCanvas.height = header.height;
  tileContext.putImageData(pixels, 0, 0);
  context.imageSmoothingEnabled = false; // Mostrar los píxeles originales, sin inventar detalles.
  const scale = view.magnification;
  context.drawImage(tileCanvas, (header.x-view.x)*scale, (header.y-view.y)*scale,
                    header.width*scale, header.height*scale);
}

async function digest(rgb) {
  return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', rgb)), byte => byte.toString(16).padStart(2,'0')).join('');
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
      setControlsEnabled(true);
      send('CREDIT_INIT', {capacityBytes: CREDIT_CAPACITY});
      $('pause-credit').disabled = false;
      $('connection').textContent = 'Conectado';
      if (resumeView && images.some(item => item.imageId === resumeView.imageId)) {
        $('image').value = resumeView.imageId;
        const saved = resumeView; resumeView = null;
        selectImage(false); level = saved.level; magnification = saved.magnification;
        x = saved.x; y = saved.y; $('level').value = level; requestView();
      } else { resumeView = null; selectImage(); }
      return;
    }
    if (message.sessionId !== sessionId) throw new Error('La respuesta pertenece a otra sesión');
    if (message.type === 'CREDIT_STATUS') { showCreditStatus(message); return; }
    // Barrera ordenada: libera bases solo después de procesar los mensajes anteriores.
    if (message.type === 'CANCELLED') { cache.release(message.viewId); grantCapacity(true); return; }
    if (message.type === 'VIEW_DONE') { cache.release(message.viewId); grantCapacity(true); }
    if (message.viewId !== viewId) return;
    if (message.type === 'RECOVERY_ACCEPTED') { $('view-state').textContent = 'Recuperando bloque…'; return; }
    if (message.type === 'VIEW_ACCEPTED') { expected = message.blocks; counters(); }
    else if (message.type === 'VIEW_DONE') {
      if (verified !== message.blocks || verified !== expected || message.full !== modes.FULL
          || message.reuse !== modes.REUSE || message.ref !== modes.REF || message.delta !== modes.DELTA
          || message.pribBytes !== pribBytes || message.recoveries !== recoveries)
        throw new Error('La vista terminó con bloques o métricas inconsistentes');
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
  const payload = new Uint8Array(data, 4 + headerLength);
  if (header.imageId !== activeView.imageId || header.level !== activeView.level
      || header.format !== 'RGB8' || !Number.isInteger(header.width) || header.width < 1 || header.width > 128
      || !Number.isInteger(header.height) || header.height < 1 || header.height > 128
      || !Number.isInteger(header.x) || !Number.isInteger(header.y) || header.x < 0 || header.y < 0
      || header.x % 128 || header.y % 128 || header.blockId !== `${header.level}:${header.x/128}:${header.y/128}`
      || !/^[0-9a-f]{64}$/.test(header.expectedHash) || payload.length !== header.payloadLength
      || seen.has(header.blockId)) throw new Error('Bloque no válido');
  // Las métricas incluyen intentos fallidos; verificados cuenta objetivos únicos.
  receivedBytes += payload.length; modes[header.mode] = (modes[header.mode] || 0) + 1; pribBytes += data.byteLength;
  if (header.mode !== 'FULL') savedBytes += header.width*header.height*3 - payload.length;
  let rgb;
  try {
    rgb = await PribDelta.reconstruct(header, payload, cache, digest);
  } catch (failure) {
    if (generation !== epoch || header.viewId !== viewId) return;
    if (!(failure instanceof BlockFailure)) throw failure;
    const count = (failures.get(header.blockId) || 0) + 1;
    if (count > 2) throw new Error('Límite de recuperación agotado');
    failures.set(header.blockId, count); recoveries++;
    cache.invalidate(header.baseImageId || header.imageId, header.baseId || header.blockId, failure.reason === 'HASH_MISMATCH');
    send('RECOVER', {viewId, transferId: header.transferId, reason: failure.reason});
    $('view-state').textContent = 'Recuperando bloque…'; counters(); return true;
  }
  if (generation !== epoch || header.viewId !== viewId) return;
  // FULL libera su buffer de red; DELTA ya creó RGB independiente y verificado.
  cache.put(header, header.mode === 'FULL' ? rgb.slice() : rgb);
  drawBlock(header, rgb, activeView);
  seen.add(header.blockId); verified++; counters();
  $('view-state').textContent = 'Verificando bloques…';
  send('ACK', {viewId, transferId: header.transferId, hash: header.expectedHash});
}

function connect() {
  // Una reconexión crea sesión y ventana nuevas; conserva solo la región elegida.
  resumeView = current ? {imageId: current.imageId, level, magnification, x, y} : resumeView;
  const generation = ++epoch;
  releasedBytes = lastGrantedBytes = 0; grantId = 0; grantsPaused = false; cache.reset();
  $('pause-credit').disabled = true; $('pause-credit').textContent = 'Pausar devoluciones';
  $('credit-available').textContent = '—'; $('credit-outstanding').textContent = '—';
  $('credit-state').textContent = 'Esperando sesión';
  socket?.close(); clearTimeout(timer); sessionId = ''; current = null; viewId = 0;
  $('error').hidden = true; $('connection').textContent = 'Conectando…';
  $('view-state').textContent = 'Esperando sesión'; $('session').textContent = '—'; $('view-id').textContent = '—';
  resetCounters();
  context.clearRect(0, 0, canvas.width, canvas.height);
  setControlsEnabled(false);
  if (!crypto.subtle) { error('Abre el visor en http://localhost para verificar SHA-256.'); return; }
  const ws = new WebSocket(`ws://${location.host}/ws`); socket = ws; ws.binaryType = 'arraybuffer';
  let chain = Promise.resolve(), queuedBytes = 0, failed = false;
  ws.onopen = () => { if (generation === epoch) send('HELLO'); };
  ws.onmessage = event => {
    // Serializar la verificación conserva el orden aunque SHA-256 sea asíncrono.
    const size = typeof event.data === 'string' ? event.data.length * 2 : event.data.byteLength;
    queuedBytes += size;
    if (queuedBytes > 32 * 1024 * 1024) { failed = true; error('Capacidad de procesamiento excedida'); ws.close(); return; }
    chain = chain.then(async () => {
      if (failed || generation !== epoch) return;
      const recovering = await receive(event.data, generation);
      // ACK verifica contenido; GRANT libera el mensaje procesado, incluso si era de una vista vieja.
      // Un intento fallido libera su buffer; RECOVER recupera el objetivo sin confundirlo con ACK.
      if (generation === epoch && typeof event.data !== 'string') {
        releasedBytes += event.data.byteLength; grantCapacity(recovering === true);
      }
    }).catch(failure => {
      failed = true; // No dibujar más bloques de una conexión cuya integridad falló.
      if (generation === epoch) { error(failure.message); ws.close(); }
    }).finally(() => { queuedBytes -= size; });
  };
  ws.onclose = () => {
    if (generation !== epoch) return;
    $('pause-credit').disabled = true; $('credit-state').textContent = 'Sesión cerrada';
    $('connection').textContent = 'Desconectado'; $('view-state').textContent = 'Conexión cerrada';
    setControlsEnabled(false);
  };
  ws.onerror = () => { if (generation === epoch) error('No se pudo conectar con el servidor local.'); };
}
$('image').onchange = () => selectImage();
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

// Herramienta de demostración: detener solo las concesiones permite observar el agotamiento.
$('pause-credit').onclick = () => {
  grantsPaused = !grantsPaused;
  $('pause-credit').textContent = grantsPaused ? 'Reanudar devoluciones' : 'Pausar devoluciones';
  if (!grantsPaused) grantCapacity(true);
};
