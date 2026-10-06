# Arquitectura y protocolo PRIB

Referencia de la implementación actual, correspondiente a las etapas 1 a 7.
PRIB envía solo los bloques de la región visible y decide si reutilizar RGB,
referenciar una base, enviar una diferencia exacta o transmitir el bloque completo.
Funciona sobre WebSocket/TCP; TCP conserva sus mecanismos de transporte y congestión.
Los créditos, prioridades, caché, generaciones y recuperación descritos aquí son
controles de la aplicación. La consolidación final de entrega sigue pendiente en etapa 8.

## Guía de lectura

- [Arquitectura](#arquitectura)
- [Bloques](#formato-de-los-bloques) y [almacenamiento](#formato-de-almacenamiento-versión-1)
- [Mensajes y sesión](#mensajes-y-sesión)
- [Créditos](#reglas-de-contabilidad)
- [Prioridades](#prioridad-y-reparto-entre-clientes), [caché](#caché-y-protección-de-bases) y [cancelación](#cancelación-y-generaciones)
- [Selección de modo](#selección-de-modo), [DELTA](#formato-xor_runs_1) y [recuperación](#recuperación-selectiva-y-límites)

Para ejecutar el proyecto, consultar el [README](../README.md); para comprobarlo,
consultar [pruebas](pruebas.md). Este documento concentra el contrato técnico.

## Arquitectura

El servidor entrega HTML/CSS/JS locales por HTTP y abre una sesión WebSocket por
cliente. El Selector atiende conexiones no bloqueantes y modifica sesiones y colas.
Cuatro trabajadores leen bloques, descomprimen, verifican hashes y calculan DELTA;
devuelven resultados al Selector. Hay como máximo un trabajo activo por sesión.
Los almacenes preparados son inmutables mientras el servidor está activo.

| Código | Responsabilidad |
| --- | --- |
| PngRows / PrepareImage | Lectura RGB8 por filas desde ZIP y pirámide de resolución |
| ImageStore / ViewRegion | Índices, bloques verificados y exportación de regiones |
| PribServer / WebSocketFrames / Json | HTTP, NIO, trabajadores y transporte de mensajes |
| PribSession / CreditWindow | Estado por sesión, selección de envíos y créditos |
| ClientCache / BlockPriority | Inventario declarado y prioridad visual/coste |
| SimilarityIndex / DeltaCodec | Bases similares y diferencias exactas XOR_RUNS_1 |
| web/app.js | Solicitudes, verificación, dibujo, métricas y conexión |
| web/cache.js / web/delta.js | RGB retenido, firmas, reconstrucción y recuperación |

PngRows admite PNG RGB de 8 bits sin entrelazado, revierte los cinco filtros y
comprueba CRC. No aplica transformaciones de color ni pretende admitir todos los PNG.
Probe e ImageIO se usan para comparar imágenes pequeñas, con límite de diez millones
de píxeles; ImageIO no participa en la preparación de imágenes gigantes.

## Formato de los bloques

- Tamaño nominal: **128 × 128**; bordes con dimensiones reales, sin relleno.
- Origen superior izquierdo, filas de izquierda a derecha y de arriba abajo.
- Cada píxel ocupa tres bytes sin signo: **R, G, B**.
- Longitud: `ancho × alto × 3`; máximo de 49,152 bytes por bloque.
- SHA-256 sobre los bytes RGB, expresado en hexadecimal minúsculo.
- JavaScript verifica longitud y hash, y agrega alfa 255 para representar RGBA.
  El alfa no forma parte del hash. Todos los modos reconstruyen el mismo RGB.


## Flujo y responsabilidades

1. `PrepareImage` inspecciona las dimensiones y calcula una cota de espacio.
2. `PngRows` entrega filas RGB y verifica filtros y CRC del PNG.
3. Cada nivel conserva hasta 128 filas, escribe sus bloques y envía filas
   reducidas al nivel siguiente. No genera imágenes intermedias completas.
4. `ImageStore.Writer` comprime cada bloque con zlib. Si no ahorra bytes, guarda
   RGB sin comprimir; en ambos casos calcula SHA-256 sobre el RGB original.
5. Se cierran los archivos y se publica `image.properties`. Solo entonces se
   retira la marca `INCOMPLETE` y la imagen queda disponible para consultar.
6. `ImageStore` localiza un bloque por su índice, lo descomprime si corresponde
   y verifica su longitud y hash. `ViewRegion` exporta una región a PNG.

Las clases y scripts incluyen comentarios en español. Para leer el flujo,
comenzar por `PrepareImage.prepare`, continuar por su clase interna `Level`
y luego por `ImageStore.readBlock`.

## Resoluciones y fidelidad

**Nivel 0 es la imagen original**, sin cambios en sus muestras RGB. Cada nivel
siguiente reduce ancho y alto a la mitad, redondeando hacia arriba. Se promedian
los canales de cada grupo 2 × 2 con división entera; en los bordes impares solo
participan los píxeles existentes. Se detiene cuando la imagen cabe en un bloque.

Ejemplo de la imagen pequeña: `782 → 391 → 196 → 98`, cuatro niveles. Los niveles
reducidos sirven para orientación; la lectura de números con el máximo detalle
utilizará siempre los bloques del nivel 0. No se aplica corrección de color.

## Formato de almacenamiento versión 1

Cada importación tiene una carpeta nueva y un `imageId` UUID. Para agregar otra
imagen basta ejecutar el mismo comando con otro nombre de entrada y salida.

| Archivo | Contenido |
| --- | --- |
| `image.properties` | Identidad, origen, RGB8, dimensiones, tamaño de bloque y niveles |
| `level-N.pack` | Datos concatenados de todos los bloques del nivel N |
| `level-N.idx` | Índice binario de registros fijos, en orden de filas de bloques |
| `result.txt` | Tamaño de salida, estimación previa, tiempo y heap observado |
| `INCOMPLETE` | Marca presente únicamente durante preparación o después de un fallo |

Cada registro del índice ocupa **48 bytes**, con enteros big-endian:

| Campo | Bytes | Significado |
| --- | --- | --- |
| offset | 8 | Posición absoluta del bloque dentro del pack, tipo long |
| length | 4 | Cantidad de bytes almacenados |
| codec | 4 | 0 para RGB directo, 1 para zlib |
| SHA-256 | 32 | Hash binario de los píxeles RGB reconstruidos |

La posición del registro es `(fila * columnas + columna) * 48`. Las dimensiones
reales del bloque se derivan del nivel y sus bordes. No es necesario cargar todo
el índice ni recorrer el pack. Una lectura recupera como máximo 49,152 bytes RGB.

## Memoria y disco

El script usa `-Xmx256m`. Los niveles conservan franjas con anchos decrecientes;
la memoria crece con el ancho de la imagen, no con su área completa. El límite de
heap no incluye toda la memoria nativa de la JVM. `result.txt` registra muestreos
del heap y no pretende medir el pico exacto ni la memoria residente del proceso.

Antes de comenzar se calcula el tamaño de todos los niveles como RGB sin comprimir,
más índices y margen para metadatos. Se exige ese espacio y una reserva adicional
de 256 MiB, aunque la compresión pueda reducir mucho la salida real. Durante la
preparación también se comprueba el espacio libre periódicamente.

Para PNG no se extrae el original ni se crean temporales completos. JPEG/GIF/BMP
utilizan un temporal comprimido acotado a 64 MiB. Los ZIP y
almacenes permanecen en el disco local y están excluidos de Git. Si una preparación
falla, los archivos parciales quedan marcados y no se sirven. No hay reanudación:
se debe repetir en una carpeta nueva; una salida existente nunca se sobrescribe.

## Mensajes y sesión

Los controles son JSON UTF-8. Los entrantes son objetos planos de texto o enteros.
Todos llevan `version: 1` y `type`; después de HELLO incluyen `sessionId`.
Frontend y servidor se actualizan juntos: es un contrato de desarrollo.

| Mensaje | Dirección | Campos y efecto |
| --- | --- | --- |
| HELLO | C → S | Negocia versión, una vez por conexión |
| IMAGE_INFO | S → C | Sesión, catálogo images, blockSize, modos y límites de crédito/caché/recuperación |
| VIEW / VIEW_UPDATE | C → S | imageId, viewId creciente, level, x, y, width, height; crea una generación |
| VIEW_ACCEPTED | S → C | Vista aceptada y bloques esperados |
| CACHE_STATE | C → S | cacheSeq consecutivo desde 1 y operation PUT, DROP o CLEAR |
| CACHE_STATE PUT | C → S | imageId, blockId, width, height, hash y similarity; declara RGB retenido |
| CACHE_STATE DROP | C → S | imageId y blockId que deja de conservar |
| CACHE_STATE CLEAR | C → S | Vacía el inventario |
| CANCEL / CANCELLED | C → S / S → C | viewId; respuesta incluye cancelledTasks, unsentBytes y outstandingBytes |
| BLOCK_FULL | S → C | Binario: mode FULL, codec RAW, RGB completo |
| BLOCK_REF | S → C | Binario: mode REUSE o REF, codec CACHE, sin payload; baseImageId, baseId y baseHash |
| BLOCK_DELTA | S → C | Binario: mode DELTA, codec XOR_RUNS_1, base y diferencia exacta |
| ACK | C → S | viewId, transferId y hash; confirma materialización, no retención ni devolución de crédito |
| RECOVER / RECOVERY_ACCEPTED | C → S / S → C | Solicitud y confirmación de recuperación selectiva; detalle más abajo |
| VIEW_DONE | S → C | Objetivos confirmados, intentos, modos, bytes y estadísticas |
| ERROR / CLOSE | S → C / C → S | Error fatal o cierre ordenado |

CACHE_STATE autoriza a suponer retención; ACK por sí solo no lo hace. Una secuencia
de inventario repetida, con huecos o incompatible genera ERROR. El inventario es
una declaración del cliente, no una prueba de su memoria física.

Cada bloque binario tiene un uint32 big-endian con longitud de cabecera, una
cabecera JSON UTF-8 y el payload. Campos comunes: version, type, sessionId,
viewId, transferId, imageId, level, blockId, x, y, width, height, format, mode,
codec, payloadLength y expectedHash. El formato es RGB8; blockId y baseId usan
`nivel:columna:fila`. expectedHash siempre describe el RGB objetivo.
REUSE exige la misma identidad, hash y geometría; REF puede cruzar imágenes o niveles
si el contenido y la geometría son idénticos. El cliente vuelve a verificar SHA-256.
La compresión zlib en disco es independiente de la representación enviada por PRIB.

## Reglas de contabilidad

El coste de un bloque es **4 + longitud de cabecera JSON UTF-8 + longitud del payload**.
No incluye controles JSON, cabeceras WebSocket, TCP/IP ni memoria del Canvas.

- Capacidad admitida: de **53.252 bytes** a **1 MiB** por sesión. El mínimo permite
  una unidad máxima: 4 + 4096 + 128 × 128 × 3 bytes.
- El visor concede **256 KiB** al iniciar la sesión.
- Se descuenta el coste exacto antes de encolar el bloque en la salida de red.
- Un mensaje preparado, pero aún no encolado, no consume crédito.
- Cambiar de vista no devuelve el crédito de los mensajes ya encolados.
- Los bloques obsoletos se descartan y devuelven capacidad al procesarse, sin
  dibujarse en la nueva vista ni confirmar materialización con ACK.
- El estado se limita a contadores; no se guarda un historial creciente de concesiones.

Sean C la capacidad inicial, S los bytes encolados y R el acumulado liberado:

```text
crédito disponible = C + R − S
bytes sin capacidad devuelta = S − R
0 ≤ crédito disponible ≤ C
0 ≤ R ≤ S
```

Puede quedar un saldo positivo insuficiente para el siguiente bloque. En ese
caso también se espera; no es necesario llegar exactamente a cero.

### Mensajes de crédito

Todos estos controles incluyen `version`, `type` y `sessionId`.

| Mensaje | Dirección y campos | Regla |
| --- | --- | --- |
| CREDIT_INIT | C → S: `capacityBytes` | Inicializa una vez. Repetir la misma capacidad no reinicia el saldo; cambiarla genera ERROR |
| CREDIT_GRANT | C → S: `grantId`, `releasedBytes` | Identificador creciente y total acumulado de bytes liberados desde el inicio de la sesión |
| CREDIT_STATUS | C → S: sin campos adicionales | Solicita el estado, incluso sin crédito |
| CREDIT_STATUS | S → C: `capacityBytes`, `availableBytes`, `outstandingBytes`, `releasedBytes`, `grantId`, `state` | Responde consultas y notifica cambios, sin repetir el mismo estado en cada vuelta |

IMAGE_INFO anuncia `maxBlockBytes` y `maxCreditBytes`. El navegador devuelve
`ArrayBuffer.byteLength` por bloque procesado, no solo la longitud de sus píxeles.
Los contadores acumulativos usan enteros de 64 bits en Java y se limitan al
máximo entero exacto de JavaScript. No se desbordan al superar 2 GiB enviados.

Una concesión con el mismo identificador y total se ignora. Una anterior cuyo
total ya está cubierto también se ignora. Una concesión nueva puede saltar
identificadores: su acumulado incorpora las anteriores. Se rechazan totales
superiores a lo enviado, decrecientes en una concesión nueva o contradictorios.
El servidor valida la contabilidad declarada; no puede comprobar la memoria
física de un cliente modificado que mienta sobre haber liberado sus buffers.

Estados: `WAIT_INIT` (sin capacidad inicial), `WAIT_CREDIT` (saldo insuficiente),
`SENDING` (trabajo pendiente) e `IDLE` (sin envíos pendientes). IDLE no significa
que todos los ACK o devoluciones de capacidad ya hayan llegado.

El cliente agrupa devoluciones hasta 16 KiB y vacía el acumulado al procesar
VIEW_DONE, CANCELLED o reanudar devoluciones. Los controles no consumen crédito
de datos; REUSE y REF sí consumen sus cabeceras binarias completas. La planificación
puede enviar una referencia pequeña aunque no alcance el saldo para un FULL.

## Prioridad y reparto entre clientes

Para los bloques que intersectan el viewport se calcula:

```text
score = 4 × fracción visible + 2 × proximidad − 3 × costePRIB / maxBlockBytes
proximidad = 1 / (1 + distancia normalizada al centro de la vista)
```

El área visible representa la ganancia de completar el bloque; proximidad mide
su cercanía al centro de interés. Todos los candidatos pertenecen al mismo nivel
solicitado, por lo que el detalle es constante en una vista. El coste incluye
la cabecera exacta del modo seleccionado. Los empates conservan el orden fila/columna.
Después de cada envío se revisan caché y créditos y se recalcula la decisión.

Los pesos se configuran al iniciar Java con `-Dprib.priority.visibility=4`,
`-Dprib.priority.proximity=2` y `-Dprib.priority.cost=3`. Deben ser finitos y no negativos.
Las propiedades se leen al inicializar la política; cambiar una requiere reiniciar.

La cola contiene como máximo 558 bloques visibles. Cada envío retira un candidato,
por lo que una vista estable con créditos termina sin inanición. El Selector da
un turno a cada sesión y rota el primer cliente entre vueltas. Se conserva un
trabajo por sesión, cuatro trabajadores y 32 tareas en espera. Un cliente lento
no bloquea los créditos ni el progreso de los demás. La evaluación actual demuestra cuatro clientes simultáneos.

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
limitada y la transmisión sigue progresando. Las bases DELTA también son RGB materializado y protegido; no se mantienen
cadenas de diferencias pendientes de reconstrucción.

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

## Selección de modo

1. Identidad completa, dimensiones y hash presentes en el inventario: REUSE.
2. Otra identidad con el mismo hash y dimensiones: REF.
3. Sin coincidencia exacta: leer y verificar el objetivo en un trabajador.
4. Consultar el índice de firmas y probar hasta cuatro bases del inventario.
5. Comparar el coste PRIB completo de cada DELTA válido con FULL.
6. Elegir DELTA solo si cumple ambos márgenes; de lo contrario enviar FULL.

La base debe estar materializada y verificada: CACHE_STATE PUT declara esa
retención. Puede proceder de un DELTA anterior ya reconstruido, pero no de una
transferencia pendiente. La caché conserva RGB completo e independiente del
mensaje diferencial original; no hay cadenas de diferencias pendientes.

El trabajador lee y verifica también la base desde su almacén. Si el hash no
coincide con la declaración del cliente o la base no puede leerse, la descarta.
Al terminar el trabajo y antes de transmitir se comprueba que siga en el inventario.
Una base invalidada durante preparación o espera lleva a FULL. Las lecturas,
firmas y diferencias se ejecutan fuera del Selector, con un trabajo por sesión.

## Firma e índice de similitud

La firma tiene **15 dígitos hexadecimales**. Representa las medias RGB de la imagen
del bloque completo y de sus cuatro cuadrantes, en ese orden. Cada canal usa
`floor(media entera / 16)`, un valor de 0 a 15. Los cuadrantes vacíos en dimensiones
de un píxel producen cero. Java y JavaScript calculan la misma firma sobre RGB.

Se calcula al materializar un bloque; el navegador la incluye en PUT. El servidor
la calcula sobre el objetivo leído. No se modifica el almacén versión 1 ni se crea
un índice de similitud de todos los bloques de una imagen gigante. El índice por
sesión tiene como máximo las 1024 entradas del inventario del cliente y no contiene
los píxeles del navegador.

Los buckets usan dimensiones y los tres dígitos de color global. Se consulta
primero el bucket exacto y después sus vecinos de ±1 por canal: como máximo 27
buckets. Se inspeccionan hasta 64 declaraciones recientes; se ordenan por suma de
diferencias absolutas de los 15 valores, con umbral 24, y se conservan hasta cuatro.
Las dimensiones deben ser idénticas, incluyendo los bordes parciales.

El índice es una instantánea inmutable reutilizable; PUT, DROP, CLEAR e invalidación
la hacen reconstruir en el siguiente acceso. Los trabajadores pueden consultar
su instantánea mientras el Selector actualiza el inventario. Una declaración
antigua no autoriza el envío si fue eliminada durante el trabajo.

Este índice es una heurística acotada: puede no encontrar la mejor base posible.
Una firma cercana no prueba que DELTA ahorre; el tamaño real de la diferencia decide.
No se hacen comparaciones todos contra todos sobre la imagen completa.

## Formato XOR_RUNS_1

Se calcula `xor[i] = base[i] XOR objetivo[i]`. Los bytes iguales no necesitan
transmitirse. Los tramos conservan literalmente los bytes XOR que cambiaron y
pueden incluir pequeños huecos de bytes iguales para ahorrar cabeceras.

| Campo del payload | Tamaño | Regla |
| --- | --- | --- |
| Longitud RGB reconstruida | uint32 BE | Igual a `width × height × 3`, máximo 49152 |
| Cantidad de tramos | uint32 BE | No mayor que la longitud RGB |
| Offset de cada tramo | uint32 BE | Posición absoluta en el RGB |
| Longitud de cada tramo | uint32 BE | Positiva y dentro del bloque |
| Bytes XOR del tramo | La longitud indicada | Se aplican sobre una copia de la base |

Los tramos se ordenan por offset y no se superponen. El decodificador rechaza
truncamientos, longitudes excesivas, desbordamientos de rango, superposición y
bytes sobrantes. Nunca modifica la base. El encoder fusiona cambios separados
por hasta ocho bytes iguales; enviar ese hueco cuesta lo mismo o menos que otro
par de enteros. Se abandona si el payload deja de ser menor que RGB directo.
Los bloques de hasta ocho bytes no usan DELTA.

El navegador comprueba SHA-256 de la base frente a `baseHash`, reconstruye y
comprueba SHA-256 del resultado frente a `expectedHash`. Solo entonces dibuja,
puede guardar el RGB y envía ACK del objetivo. Un resultado aproximado nunca se dibuja.

## Coste, ahorro y créditos

```text
coste FULL = 4 + cabecera FULL UTF-8 + RGB
coste DELTA = 4 + cabecera DELTA UTF-8 + payload XOR_RUNS_1
aceptar si DELTA ≤ FULL × 0,85 y FULL − DELTA ≥ 256 bytes
```

Los márgenes predeterminados son **15 % y 256 bytes**, configurables al iniciar
Java con `-Dprib.delta.minSaving=0.15` y `-Dprib.delta.minBytes=256`. El porcentaje
está en el intervalo [0,1) y el mínimo absoluto no puede ser negativo. La comparación
incluye identificadores, referencia de base, hashes y formato de la diferencia.
Entre candidatos que cumplen los márgenes se elige el menor paquete PRIB.

La prioridad usa el coste estimado FULL antes de leer el objetivo y el coste
exacto del bloque preparado después de calcular la diferencia. Una referencia
exacta de mayor prioridad puede adelantarse al preparado. Si FULL no cabe pero
hay bases similares, se permite preparar un candidato para conocer su coste real;
**preparar no consume crédito ni autoriza transmisión**. Si el resultado no cabe,
se conserva un único preparado y se espera. Referencias exactas elegibles pueden
seguir progresando mientras espera.

Todos los modos y reintentos descuentan exactamente su mensaje binario PRIB al
encolarse. RECOVER y ACK no devuelven crédito. El cliente libera el buffer de un
intento fallido y envía una concesión acumulativa; forzar su envío evita esperar
al umbral de 16 KiB después de un DELTA pequeño. Pausar devoluciones también
puede detener la recuperación FULL hasta que se conceda capacidad.

## Detalles de mensajes diferenciales y finalización

El catálogo anuncia `modes: "FULL,REUSE,REF,DELTA"`, `deltaCodec: "XOR_RUNS_1"`
y `maxRecoveries: 2`. El servidor y el frontend deben actualizarse juntos.
Los controles entrantes siguen siendo JSON plano. Se admiten hasta 32 campos,
con el límite previo de 8 KiB por control y 4096 bytes por cabecera binaria.

| Mensaje o campo | Dirección | Contrato |
| --- | --- | --- |
| CACHE_STATE PUT `similarity` | C → S | Firma hexadecimal de 15 dígitos del RGB retenido |
| BLOCK_DELTA | S → C | `mode=DELTA`, `codec=XOR_RUNS_1`, `baseImageId`, `baseId`, `baseHash`, `expectedHash` y payload |
| BLOCK_REF `baseHash` | S → C | Hash del RGB de la base, además de la identidad de la base |
| RECOVER | C → S | `viewId`, `transferId`, `reason` de un intento pendiente |
| RECOVERY_ACCEPTED | S → C | `viewId`, `transferId` fallido, `blockId`, `reason`, `attempt` y `mode=FULL` |
| VIEW_DONE | S → C | Objetivos, intentos, modos, bytes, recuperaciones y coste de selección diferencial |

PUT sin firma todavía permite coincidencia exacta REUSE/REF, pero no aporta esa
entrada al índice DELTA. El frontend actual calcula y anuncia siempre la firma
al conservar RGB. Todos los mensajes conservan versión, tipo y sesión. La cabecera
binaria conserva geometría, vista, transferencia y longitud de payload descritas arriba.

VIEW_DONE requiere que todos los objetivos tengan ACK, además de terminar
los envíos. `blocks` cuenta objetivos únicos; `transmissions` cuenta intentos,
incluyendo fallidos. `full`, `reuse`, `ref`, `delta` y `pribBytes` también cuentan
intentos. El panel Payload recibido suma RGB FULL y diferencias DELTA; el campo
Bytes PRIB agrega las cabeceras binarias. `recoveries` cuenta solicitudes aceptadas. El cliente coteja esas métricas
con lo recibido antes de declarar Vista completa. IDLE del crédito puede aparecer
antes del último ACK y no equivale por sí solo a vista completa.

`avoidedRgbBytes` suma RGB objetivo menos payload para los modos distintos de FULL.
No descuenta cabeceras adicionales ni el FULL de recuperación; no es el ahorro neto
frente a un recorrido equivalente. `deltaCandidates` cuenta bases evaluadas;
`deltaNanos` mide firmas, búsqueda, lecturas de bases y generación de diferencias,
no CPU pura ni la lectura inicial del objetivo. Los trabajos cancelados no se
incluyen en el resumen de la generación nueva.

## Recuperación selectiva y límites

| Motivo | Detección del navegador | Acción |
| --- | --- | --- |
| BASE_MISSING | No encuentra la base anunciada | Invalida esa identidad y pide FULL del objetivo |
| CACHE_MISS | Hash declarado o geometría de base no corresponden | Actualiza el inventario y pide FULL |
| HASH_MISMATCH | SHA-256 de base o resultado es incorrecto | Vacía el inventario de caché y pide FULL |
| DELTA_FAILED | Payload diferencial mal formado | Descarta el resultado y pide FULL |

El navegador invalida antes de enviar RECOVER. Un hash incorrecto vacía la caché
como medida conservadora porque varios aliases pueden compartir RGB. La operación
CLEAR mantiene las protecciones de generaciones, con conjuntos vacíos, y la secuencia
de inventario; los nuevos FULL se vuelven a proteger al guardarlos. Invalidar una
base por integridad está permitido incluso si estaba protegida: sus dependencias
recuperan, en vez de usar datos dañados.

El servidor exige una vista actual y una transferencia sin ACK. Usa esa transferencia
para identificar el objetivo; el cliente no puede pedir un bloque arbitrario con
RECOVER. Elimina ese intento pendiente, invalida sus declaraciones de base/objetivo,
reencola solamente el objetivo y obliga FULL, sin buscar otra base durante el reintento.
Permite **dos reintentos por objetivo y vista**. Un tercer fallo genera ERROR y cierre.
Un RECOVER repetido de un intento ya retirado o con razón incompatible es inválido.

RECOVER de una generación sustituida o cancelada se ignora. CANCEL conserva los
bytes en tránsito y descarta las recuperaciones pendientes de esa generación.
VIEW_DONE/ACK mantienen protegidas las bases hasta que finaliza la materialización;
CANCELLED sigue siendo la barrera ordenada que libera generaciones antiguas.
No se agregan grafos profundos ni diferencias basadas en objetivos aún pendientes.

Errores de cabecera, sesión, protocolo, almacén del objetivo o agotamiento de reintentos
cierran la conexión. Reconectar sigue siendo la recuperación manual final.

## Visor, reconexión y límites

El nivel 0 conserva la resolución original. El zoom superior al 100 % amplía sus
píxeles sin inventar detalle; al alejar se utilizan niveles reducidos. La región
solicitada se calcula a partir del área visible y la escala. Los bloques que la
intersectan pueden incluir píxeles exteriores al rectángulo exacto.
Los cambios de vista descartan dibujos obsoletos, conservando su deuda de crédito.

Reconectar crea una sesión nueva, vacía inventario y contadores, negocia capacidad
y vuelve a solicitar la región. Las verificaciones viejas no modifican la sesión
nueva. No hay reintentos automáticos indefinidos.

| Recurso | Límite |
| --- | --- |
| Conexiones / imágenes de catálogo | 32 / 100 |
| Región por VIEW | 3840 × 2160, hasta 558 bloques |
| Trabajadores / tareas en espera | 4 / 32 |
| Salida por cliente | 1 MiB y 128 elementos |
| Cabecera HTTP / control entrante | 8 KiB |
| Cabecera binaria / campos de control | 4096 bytes / 32 |
| Cola de verificación del navegador | 32 MiB |

Ping cada 20 segundos y cierre tras 60 sin respuesta; HTTP incompleto vence en
10 segundos. El servidor escucha en 127.0.0.1, sin TLS ni acceso desde otros equipos.
SHA-256 del navegador funciona en el contexto local de localhost. El heap Java de
256 MiB no incluye toda la memoria de la JVM; la caché RGB tampoco es toda la RAM de Chrome.

## Preparación desde la web

ImagePreparation administra una sola tarea global. PREPARE_LIST enumera ZIP
directos del directorio de imágenes; PREPARE_ENTRIES recibe `zip` y devuelve
entradas PNG/JPEG/GIF/BMP. Las consultas usan un trabajador, con una consulta pendiente por
sesión. No se extraen entradas ni se aceptan rutas exteriores.

PREPARE_START recibe `zip`, `entry` y `name`. El destino es una carpeta nueva
dentro del directorio de datos. Ejecuta PrepareImage en un proceso Java separado
con heap de 256 MiB, conservando las verificaciones y la marca INCOMPLETE.
La tarea no ocupa los cuatro trabajadores que sirven bloques.

PREPARE_STATUS devuelve `state` (IDLE/RUNNING/DONE/FAILED), `percent`, `message`
y, cuando hay tarea, `name`. El panel consulta cada dos segundos; el preprocesador
informa filas aproximadamente cada cinco. PREPARE_ERROR comunica errores de
solicitud sin cerrar el visor. Todos estos controles llevan versión y sesión.

Al terminar, el Selector incorpora el almacén y envía CATALOG_UPDATE con `images`
a las sesiones conectadas. Se conserva la vista seleccionada; con catálogo vacío
se muestra la primera imagen disponible. Preparar externamente desde la terminal
sigue requiriendo reinicio para descubrir ese almacén.

Cerrar el panel no cancela la tarea; cerrar el servidor detiene el proceso hijo.
Las salidas incompletas no se publican ni se eliminan automáticamente. No se
sobrescriben destinos existentes. Es una utilidad local de gestión, independiente
de los modos de transferencia PRIB.

### Fuentes de imagen y URL

Se conserva PREPARE_START para ZIP. PREPARE_URL recibe `url` y `name`; abre
un flujo HTTP/HTTPS público con hasta cinco redirecciones, 10 segundos para
conectar y 30 segundos de espera por lectura. No envía credenciales ni cookies.
Cada destino se valida para rechazar redes privadas, locales y URLs con usuario.

PREPARE_UPLOAD recibe `size` y `name`; reserva la tarea para la sesión emisora.
PREPARE_UPLOAD_ACK con `offset: 0` permite comenzar. PREPARE_CHUNK recibe
`offset` y `data` en Base64, hasta 4096 bytes decodificados. Se permite un solo
fragmento pendiente; el ACK devuelve el siguiente offset cuando se escribió
al flujo del preprocesador. No se acumula el archivo completo en memoria ni
se crea una copia completa en memoria durante la recepción. Estos controles respetan el límite de
8 KiB y no consumen los créditos de bloques PRIB de salida.

PREPARE_END confirma que llegaron los bytes declarados y cierra el flujo. Solo
entonces se puede quitar INCOMPLETE y publicar el almacén. PREPARE_ABORT o la
desconexión de la sesión propietaria antes de END interrumpen la subida. También
se aborta tras 60 segundos sin actividad. Otras sesiones no pueden escribirla.
La preparación final continúa al cerrar el panel o después de END. ZIP y URL
continúan al cerrar la pestaña. Todos los controles conservan versión y sesión.

Las tres fuentes usan la misma pirámide, hashes y catálogo. ImageRows detecta
firmas PNG (89 50 4e 47 0d 0a 1a 0a), JPEG (ff d8 ff), GIF87a/GIF89a y BMP (BM).
PNG delega al lector incremental RGB8 existente. JPEG/GIF/BMP usan ImageIO,
un temporal comprimido de hasta 64 MiB (eliminado después de decodificar),
16 millones de píxeles y hasta 32768 por lado. Se valida la dimensión antes de
reservar la imagen decodificada. Se convierte a RGB8; GIF usa el primer fotograma
y el alfa se compone sobre blanco. `sourceFormat` registra el formato detectado
en el manifiesto. Cambiar la fuente no reduce el espacio de salida.

Los nombres de controles PREPARE_UPLOAD/CHUNK/END/ABORT se conservan para todos
los formatos. Los nombres y extensiones del archivo local no determinan su firma.
Los hashes corresponden al RGB decodificado, sin prometer recuperar la pérdida
original de JPEG. PNG sigue siendo la vía para imágenes de decenas de GB.
`prib.allowLocalImageUrls=true` se usa exclusivamente en las pruebas aisladas
para servir fixtures HTTP locales; el servidor normal rechaza esos destinos.

## Referencias

- [Formato PNG y filtros](https://www.w3.org/TR/png-3/)
- [WebSocket, RFC 6455](https://www.rfc-editor.org/rfc/rfc6455.html)
- [TCP, RFC 9293](https://www.rfc-editor.org/rfc/rfc9293.html)

XOR_RUNS_1, las firmas, pesos y presupuestos son decisiones propias de esta
implementación de la propuesta PRIB; no se presentan como un estándar ni como VCDIFF.
