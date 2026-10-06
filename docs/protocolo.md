# Implementación de PRIB

PRIB es un protocolo de aplicación sobre **WebSocket/TCP**. HTTP entrega el visor;
PRIB controla regiones, bloques, reutilización, créditos y recuperación. El control
de congestión del transporte sigue correspondiendo a TCP.

## Arquitectura y almacenamiento

| Código | Responsabilidad |
| --- | --- |
| PribServer / PribSession | Selector asíncrono, conexiones y estado independiente por cliente |
| PrepareImage / PngRows / ImageRows / ImagePreparation | Preparación por niveles y publicación de imágenes |
| ImageStore | Acceso por índice a bloques de los packs, sin cargar la imagen completa |
| CreditWindow / ClientCache / BlockPriority | Capacidad, inventario del cliente y prioridad |
| SimilarityIndex / DeltaCodec | Bases similares y diferencias exactas |
| web/app.js, cache.js, delta.js, metrics.js | Visor, caché RGB, reconstrucción y métricas |
| SessionLog | Eventos resumidos por sesión en la terminal |

El Selector mantiene sesiones y colas; cuatro trabajadores realizan disco, hash y
DELTA, con un trabajo activo por sesión. La preparación usa un proceso separado.

Los bloques son RGB8 de hasta **128 × 128** píxeles. El nivel 0 conserva el original;
los siguientes reducen a la mitad promediando grupos 2 × 2. SHA-256 verifica los
bytes RGB, no el PNG ni el contenido comprimido. Ampliar sobre el 100 % agranda
píxeles, sin crear detalle.

Cada almacén contiene `image.properties`, packs e índices por nivel y `result.txt`.
Los índices tienen registros de 48 bytes (offset, longitud, códec y hash); se leen
por posición. Los packs almacenan RAW o zlib. `INCOMPLETE` impide publicar una
preparación interrumpida. Los formatos y requisitos de disco están en
[preparación](preparacion-equipo.md).

## Flujo y mensajes

1. `HELLO → IMAGE_INFO`: sesión, catálogo y límites.
2. `CREDIT_INIT` y `VIEW`: capacidad inicial, imagen, nivel y región.
3. `VIEW_ACCEPTED`: cantidad de bloques requeridos.
4. El servidor selecciona y envía bloques; el cliente reconstruye, verifica y dibuja.
5. `CACHE_STATE` informa retención, `ACK` confirma integridad y `CREDIT_GRANT` devuelve capacidad.
6. `VIEW_DONE` confirma todos los objetivos y resume la transferencia.

Los controles son JSON con `version`, `type` y, tras HELLO, `sessionId`.
`viewId` identifica la generación, `transferId` un intento y `blockId` usa
`nivel:columna:fila`. Las coordenadas pertenecen al nivel solicitado.
Cada mensaje binario contiene `uint32 BE longitudCabecera | JSON | payload`;
la cabecera identifica objetivo, geometría, modo, hash y base cuando corresponde.

## Modos de envío

| Modo | Cuándo se usa | Datos enviados |
| --- | --- | --- |
| FULL | No hay una base válida o ahorro suficiente | RGB completo |
| REUSE | El mismo bloque sigue en caché con geometría y hash correctos | Referencia sin payload |
| REF | Otro bloque retenido tiene geometría y contenido idénticos | Referencia sin payload |
| DELTA | Una base similar permite ahorrar al menos 15 % y 256 bytes frente a FULL | Diferencias XOR_RUNS_1 |

REUSE y REF viajan como `BLOCK_REF`; los demás como `BLOCK_FULL` y `BLOCK_DELTA`.
La búsqueda usa firmas RGB y hasta cuatro bases candidatas del inventario del cliente.
La similitud solo propone bases: el hash verifica contenido y el tamaño completo
del paquete decide el ahorro. XOR_RUNS_1 aplica tramos XOR sobre una copia de la base;
el RGB reconstruido siempre se verifica. Una base recibida por DELTA ya está materializada.

## Créditos, prioridad y caché

- **Créditos:** ventana de 256 KiB por cliente. Cada envío descuenta prefijo, cabecera
  y payload; también REUSE/REF y reintentos. Las devoluciones son acumulativas y no
  duplican capacidad al repetirse. ACK, retención en caché y devolución son independientes.
- **Prioridad:** combina fracción visible, cercanía al centro y coste estimado;
  después de preparar se utiliza el coste real. El Selector rota entre clientes
  y limita escrituras por turno.
- **Caché:** hasta 16 MiB de RGB y 1024 entradas. La expulsión considera recencia,
  proximidad, frecuencia y reutilización; protege bases en uso. Puede dibujar sin
  retener cuando no hay espacio. El servidor conserva el inventario de metadatos.

## Generaciones y recuperación

Al cambiar la vista, `CANCEL → CANCELLED` descarta trabajo obsoleto y libera bases
cuando los mensajes anteriores han sido procesados. Los datos antiguos no se dibujan,
pero los bytes ya enviados conservan su deuda hasta su devolución. Reconectar crea
una sesión nueva y reinicia caché y créditos.

`RECOVER` distingue `BASE_MISSING`, `CACHE_MISS`, `HASH_MISMATCH` y `DELTA_FAILED`.
Se reenvía únicamente el objetivo mediante FULL, con hasta dos reintentos por bloque
y vista. Cabeceras inválidas, errores del almacén o exceso de reintentos cierran la
conexión. La cancelación de generaciones resuelve la obsolescencia.

## Preparación, límites y red

Se prepara una imagen a la vez desde ZIP, archivo local o URL. La publicación
actualiza el catálogo sin reiniciar. Los destinos existentes no se sobrescriben;
la subida local requiere mantener la pestaña conectada hasta terminar.

| Recurso | Límite |
| --- | --- |
| Conexiones / catálogo | 32 / 100 imágenes |
| Región solicitada | 3840 × 2160; hasta 558 bloques |
| Trabajadores / cola | 4 / 32 |
| Salida por cliente | 1 MiB y 128 buffers |
| Cola de procesamiento del navegador | 32 MiB |
| Heap de servidor y preparador | 256 MiB por proceso; no equivale a RAM total |

El modo normal escucha en 127.0.0.1; `-Red` escucha en todas las interfaces IPv4.
Host se limita a localhost y direcciones propias detectadas al arrancar; el Origin
WebSocket debe coincidir cuando está presente. WebSocket y CSP usan el host validado.
La [guía del equipo](preparacion-equipo.md#uso-entre-dos-pcs-con-radmin-vpn) explica Firewall y acceso.

SHA-256 usa Web Crypto en localhost y la copia local de
[js-sha256 0.11.1](https://github.com/emn178/js-sha256/tree/v0.11.1) en HTTP por IP,
con [licencia MIT](../web/vendor/LICENSE-js-sha256.txt). No hay CDN ni descargas al
abrir el visor. SHA-256 verifica integridad, no identidad; el servidor no incluye
TLS ni autenticación propia.

## Métricas

El panel separa datos **de la vista** de caché y créditos **de la sesión**.
Los modos cuentan transmisiones, incluidos reintentos; bloques verificados cuenta
objetivos únicos. El éxito SHA-256 se muestra al completar la vista.

- **Bytes PRIB:** prefijo, cabecera y payload; excluye controles y WebSocket/TCP/IP.
- **RGB evitado:** ahorro bruto de payload, no ahorro neto de toda la conexión.
- **Tiempos:** total, primer bloque y espera por crédito; no son RTT.
- **Caché media:** promedio ponderado por tiempo. **Bloques resueltos con caché:**
  proporción verificada mediante REUSE/REF/DELTA. **Concesiones:** devoluciones de capacidad.

`VIEW_DONE` cierra las métricas de vista. La terminal resume eventos por sesión y
agrupa ráfagas; su duración termina al confirmar la vista en el servidor.
Consultar [demostración](demostracion.md) y [resultados](pruebas.md#resultados-e-interpretación).

## Referencias técnicas

[WebSocket: RFC 6455](https://www.rfc-editor.org/rfc/rfc6455.html),
[TCP: RFC 9293](https://www.rfc-editor.org/rfc/rfc9293.html),
[SHA-256: FIPS 180-4](https://csrc.nist.gov/pubs/fips/180-4/upd1/final).
XOR_RUNS_1, firmas, prioridades y políticas de caché son decisiones de PRIB.
