# Etapa 5 — Reutilización, prioridades, caché y generaciones

> Documento del cierre de etapa 5. DELTA, RECOVER y la finalización tras ACK
> se describen en el contrato actual de [etapa 6](etapa-6.md).

## Alcance y resultado

Esta etapa implementa el nivel A de la propuesta PRIB: FULL, REUSE y REF,
prioridad por valor visual y coste, inventario por cliente, caché limitada,
créditos y cancelación explícita por generación. Conserva Java 21, los almacenes
versión 1 y el canal WebSocket/TCP de las etapas anteriores. No hay que preparar
nuevamente las imágenes existentes.

DELTA lossless, firmas de similitud y RECOVER selectivo corresponden a etapa 6.
Un fallo de integridad o una base declarada que no existe cierra la conexión;
Reconectar crea una sesión nueva con caché vacía y solicita la región seleccionada.

## Decisión de reutilización

El servidor lee solamente los registros de índice de los bloques visibles en un
trabajador, sin cargar el índice de toda la imagen. Los descriptores incluyen
geometría y SHA-256 del RGB canónico. El inventario de cada sesión tiene un índice
por hash y dimensiones que permite localizar contenido exacto.

1. Mismo `imageId` y `blockId`, hash y geometría: **REUSE**.
2. Otra identidad con el mismo hash y geometría: **REF**.
3. Sin base exacta confirmada por el inventario: **FULL**.

FULL lee, descomprime y verifica el bloque en un trabajador. REUSE/REF envían una
cabecera binaria sin payload RGB; el navegador obtiene RGB de la base y vuelve a
verificar SHA-256 antes de dibujar y enviar ACK. La geometría también debe coincidir:
una franja de borde nunca se referencia como un bloque de otras dimensiones.
La coincidencia exacta puede cruzar imágenes o niveles; la identidad completa de
una base incluye `baseImageId` y `baseId`.

ACK confirma materialización. **ACK no anuncia retención en caché**; solamente
CACHE_STATE autoriza al servidor a suponer que el cliente conserva contenido.
El inventario es una declaración del cliente; un cliente modificado puede mentir.
Los almacenes preparados se consideran inmutables durante la ejecución.

## Mensajes y contrato de desarrollo PRIB 1

Se conserva la versión 1 y se agrega `modes: "FULL,REUSE,REF"`, `maxCacheBytes`
y `maxCacheEntries` a IMAGE_INFO. El frontend y el servidor deben actualizarse juntos.
Los controles entrantes continúan siendo objetos JSON planos de texto y enteros.

| Mensaje | Dirección y campos | Efecto |
| --- | --- | --- |
| CACHE_STATE | C → S: `cacheSeq`, `operation` | Actualiza el inventario de esa sesión |
| CACHE_STATE PUT | Además: `imageId`, `blockId`, `width`, `height`, `hash` | Declara RGB materializado y conservado |
| CACHE_STATE DROP | Además: `imageId`, `blockId` | Elimina una declaración de retención |
| CACHE_STATE CLEAR | Sin campos de bloque obligatorios | Vacía el inventario |
| VIEW / VIEW_UPDATE | C → S: mismos campos VIEW de etapa 3 | Crea una generación creciente y reprioriza |
| CANCEL | C → S: `viewId` | Cancela trabajo pendiente de esa generación |
| CANCELLED | S → C: `viewId`, `cancelledTasks`, `unsentBytes`, `outstandingBytes` | Confirma la barrera de cancelación |
| BLOCK_FULL | S → C binario: `mode=FULL`, `codec=RAW` | Envía RGB completo |
| BLOCK_REF | S → C binario: `mode=REUSE` o `REF`, `codec=CACHE`, `baseImageId`, `baseId` | Resuelve usando RGB conservado |
| ACK | C → S: `viewId`, `transferId`, `hash` | Confirma materialización verificada de cualquiera de los tres modos |
| VIEW_DONE | S → C: `viewId`, `blocks`, `full`, `reuse`, `ref`, `pribBytes`, `avoidedRgbBytes` | Termina el envío y resume los modos |

Todos llevan `version`, `type` y `sessionId`. CACHE_STATE es incremental:
`cacheSeq` comienza en 1 y aumenta exactamente en uno. Una secuencia repetida,
con huecos o contradictoria genera ERROR, al igual que una entrada que exceda
presupuesto, geometría o formato de hash. TCP entrega los controles en orden;
no hace falta guardar un historial creciente de actualizaciones.

El mensaje binario conserva `uint32 BE + JSON UTF-8 + payload`. Añade `mode` a
los campos de etapa 3. BLOCK_REF tiene `payloadLength=0`; `expectedHash` siempre
identifica el RGB objetivo. `baseId` usa el formato `nivel:columna:fila`.
REUSE requiere que la identidad de base sea exactamente la del objetivo.

## Créditos y capacidad de control

**Todos los modos consumen bytes PRIB exactos**: 4 + cabecera JSON UTF-8 + payload.
REUSE/REF no se tratan como controles gratuitos. CREDIT_GRANT, CACHE_STATE, CANCEL,
ACK y los controles de estado continúan circulando sin crédito de datos.
Cambiar de vista no devuelve capacidad de mensajes ya encolados.

La capacidad inicial sigue siendo 256 KiB. El navegador agrupa las devoluciones
hasta 16 KiB y vacía el acumulado al procesar VIEW_DONE, CANCELLED o reanudar las
concesiones. Pausar devoluciones conserva el acumulado sin retener buffers de red.
Un bloque descartado por pertenecer a una generación anterior también devuelve
capacidad después de procesarse, sin dibujarse ni confirmar materialización.

La planificación elige el candidato de mayor prioridad cuyo coste cabe en el
saldo. Así, una referencia pequeña puede progresar aunque no alcance para FULL.
La unidad máxima y las fórmulas contables de [etapa 4](etapa-4.md) se mantienen.

## Prioridad y reparto entre clientes

Para los bloques que intersectan el viewport se calcula:

```text
score = 4 × fracción visible + 2 × proximidad − 3 × costePRIB / maxBlockBytes
proximidad = 1 / (1 + distancia normalizada al centro de la vista)
```

El área visible representa la ganancia de completar el bloque; proximidad mide
su cercanía al centro de interés. Todos los candidatos pertenecen al mismo nivel
solicitado, por lo que el detalle es constante en esta etapa. El coste incluye
la cabecera exacta del modo seleccionado. Los empates conservan el orden fila/columna.
Después de cada envío se revisan caché y créditos y se recalcula la decisión.

Los pesos se configuran al iniciar Java con `-Dprib.priority.visibility=4`,
`-Dprib.priority.proximity=2` y `-Dprib.priority.cost=3`. Deben ser finitos y no negativos.
Las propiedades se leen al inicializar la política; cambiar una requiere reiniciar.

La cola contiene como máximo 558 bloques visibles. Cada envío retira un candidato,
por lo que una vista estable con créditos termina sin inanición. El Selector da
un turno a cada sesión y rota el primer cliente entre vueltas. Se conserva un
trabajo por sesión, cuatro trabajadores y 32 tareas en espera. Un cliente lento
no bloquea los créditos ni el progreso de los demás. Las pruebas con dos clientes
verifican aislamiento; una evaluación de carga amplia pertenece a etapa 7.

## Caché y protección de bases

La caché del navegador conserva hasta **16 MiB de RGB y 1024 entradas**. Los aliases
REF comparten RGB inmutable, pero se cobran por entrada: la contabilidad es
conservadora y también limita los metadatos. Cada FULL almacenado copia su RGB
para liberar el ArrayBuffer de transporte después de procesarlo.

La política no es solamente LRU. Su utilidad combina recencia, proximidad al
viewport actual, frecuencia y cantidad de aliases exactos que pueden servir como
referencias. Los pesos actuales son 3, 3, 2 y 1; el potencial se satura en 2.
La recencia se mide en accesos, no en tiempo de reloj. La proximidad se aplica
solo a bloques de la misma imagen y nivel; la frecuencia se satura a ocho accesos.
Antes de proteger una nueva vista se reserva espacio para sus bloques ausentes,
hasta la mitad del presupuesto. Se expulsan por utilidad entradas que no están
protegidas ni intersectan esa nueva vista. Esta reserva permite renovar la caché
al navegar; una vista repetida conserva sus objetivos de REUSE. Al faltar espacio
durante la recepción se compara la utilidad del nuevo bloque con la menor utilidad
de las entradas no protegidas. Se reporta DROP antes de PUT. Puede dibujarse un
bloque y enviarse ACK sin conservarlo ni reportar PUT.

Antes de enviar VIEW, el navegador protege el inventario actual para esa generación.
Los bloques añadidos mientras hay generaciones activas también quedan protegidos.
No se expulsa una base que podría estar referenciada por un mensaje en tránsito.
VIEW_DONE y CANCELLED liberan la protección al procesarse en la misma cadena
ordenada de verificación; liberar una generación no libera las otras. Se permiten
hasta 64 generaciones protegidas; si se alcanza el límite, la siguiente solicitud
espera y vuelve a intentarse sin aumentar `viewId`.

Esta protección es conservadora: protege todo el inventario durante una vista,
no solamente las bases elegidas por el servidor. Cuando no hay candidatos de
expulsión, el cliente dibuja nuevos bloques sin almacenarlos. La memoria permanece
limitada y la transmisión sigue progresando. La protección específica de bases
DELTA y los grafos de dependencia se incorporarán con ese modo en etapa 6.

El límite de RGB no incluye Canvas, RGBA temporal, objetos JS ni la cola de recepción,
que conserva el máximo anterior de 32 MiB. No se afirma que 16 MiB sea toda la RAM
consumida por el navegador. Reconectar vacía caché, inventario y contadores de sesión.

## Cancelación y generaciones

El navegador envía CANCEL de la generación anterior y luego VIEW con un número
mayor. VIEW también cancela la anterior si no recibió CANCEL explícito. Un CANCEL
atrasado es idempotente y no cancela una generación nueva; uno futuro es inválido.

La cancelación elimina pendientes y datos preparados sin enviar. Los trabajadores
comprueban un indicador de cancelación antes de leer; la lectura de metadatos lo
comprueba entre bloques. Una lectura ya iniciada puede terminar, pero su resultado
obsoleto se descarta. En una actualización VIEW directa se conservan los descriptores pendientes que
todavía intersectan la nueva vista y se repriorizan. CANCEL explícito descarta
esos pendientes; el navegador actual usa ese recorrido. En ambos recorridos el
contenido ya confirmado se reutiliza por REUSE/REF. Una lectura FULL obsoleta
no se transfiere a otra generación.

CANCELLED se encola detrás de los datos anteriores y el servidor deja de producir
datos para esa generación. El cliente lo procesa después de las verificaciones
anteriores. Es la barrera que permite liberar bases antiguas con seguridad.
`unsentBytes` cuenta el mensaje preparado descartado, no los bytes en tránsito;
`cancelledTasks` registra pendientes y trabajo activo, sin pretender medir lecturas
físicas evitadas. `outstandingBytes` conserva la deuda real de la ventana.

## Cómo demostrar la etapa

```powershell
./scripts/servidor.ps1
```

1. Abrir http://localhost:8080 y Mostrar transferencia.
2. Elegir una imagen pequeña, nivel 0 y esperar Vista completa.
3. Pulsar Ir a la región sin cambiar coordenadas: deben aumentar los bloques REUSE
   y caer los bytes PRIB/RGB recibidos. Comparar los contadores de la primera vista.
4. Con la imagen de 17 GB, navegar por regiones y volver a una reciente. Puede
   haber FULL si la región ya fue expulsada o excedió el presupuesto de caché.
5. Cambiar rápidamente nivel, región y zoom. Esperar la última vista; no debe
   mezclar bloques de generaciones distintas ni dejar bases protegidas indefinidamente.
6. Pausar devoluciones y saltar a una región no visitada al 100 %. Debe agotarse
   el saldo; otra pestaña debe continuar. Reanudar y comprobar saldo sin deuda.
7. Reconectar: cambia la sesión y vuelve a solicitarse la región con inventario vacío.

REF necesita bloques distintos con contenido y geometría idénticos; no basta
con parecido visual. La prueba de red genera dos almacenes de una misma imagen
sintética para demostrarlo de manera reproducible, sin duplicar los almacenes gigantes.

```powershell
./scripts/test.ps1
# Opcional: política JS sin navegador, requiere Node.js y ningún paquete externo.
node ./scripts/test-cache.cjs
# Opcional: Chrome con servidor activo; preparación en pruebas-navegador.md.
node ./scripts/browser/reuse.cjs
```

## Evidencia del 3 de octubre de 2026

Se ejecutó la suite Java completa en Windows con JDK 21.0.11 y heap máximo de 256 MiB. Pasaron
PNG, almacén, créditos, caché/prioridad y HTTP/WebSocket. La integración demostró
FULL inicial, REUSE al volver, REF entre imágenes idénticas, geometría de bordes,
CLEAR, inventarios independientes, CANCEL sin devolver crédito y referencias
que caben cuando un FULL ya no cabe. La prueba JS comprobó presupuesto, utilidad,
protección entre generaciones, límite de generaciones y reinicio.

En Chrome se verificaron los tres modos con una imagen sintética de 320 × 260:

| Vista | FULL | REUSE | Bytes PRIB binarios |
| --- | --- | --- | --- |
| Primera región completa | 9 | 0 | 252995 |
| Misma región otra vez | 0 | 9 | 4020 |

La reducción en mensajes binarios de esa prueba fue aproximadamente **98,4 %**.
No incluye controles JSON ni cabeceras WebSocket/TCP/IP; no extrapola el ahorro
a todas las imágenes. Cambiar al segundo almacén idéntico resolvió nueve bloques
por REF sin payload RGB. También pasaron generaciones rápidas, dos clientes,
reconexión y devolución de crédito.

Las capturas y temporales quedan en `build/browser-check/` y están fuera de Git.
La suite completa de navegador pasó con navegación de la imagen de 17 GB, zoom, bordes,
ventana 4K, panel, pantalla completa y control de créditos. Los resultados se
registran como pruebas de desarrollo; no sustituyen la evaluación de carga y
las mediciones sistemáticas de etapa 7.

## Archivos principales

| Archivo | Responsabilidad |
| --- | --- |
| `ImageStore.java` | Consulta de descriptores por registro y lectura RGB verificada |
| `ClientCache.java` | Inventario por sesión e índice de contenido exacto |
| `BlockPriority.java` | Puntuación configurable por visibilidad, centro y coste |
| `PribSession.java` | RBS, prioridades, créditos, modos, ACK y generaciones |
| `PribServer.java` | Turnos rotativos y recursos web locales |
| `web/cache.js` | Caché RGB, utilidad, inventario y protección de bases |
| `web/app.js` | Reconstrucción exacta, SHA-256, dibujo y métricas |
| `ClientCacheTest.java`, `PribServerTest.java` | Pruebas de inventario, políticas y protocolo real |
| `scripts/test-cache.cjs`, `scripts/browser/reuse.cjs` | Política JS y demostración en Chrome |

La referencia de requisitos es la propuesta técnica revisada entregada por el
equipo. Se adoptan sus responsabilidades RBS, prioridad visual/coste, créditos y
caché por cliente; los valores de pesos y presupuestos son decisiones de esta
implementación. La siguiente etapa implementará DELTA lossless y RECOVER selectivo.
