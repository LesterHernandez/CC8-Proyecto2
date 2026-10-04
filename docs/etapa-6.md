# Etapa 6 — DELTA exacto y recuperación selectiva

## Alcance y resultado

Esta etapa completa los cuatro modos de PRIB: REUSE, REF, DELTA y FULL. Añade
firmas de similitud, candidatos acotados, una diferencia lossless y recuperación
selectiva de un bloque. Conserva SHA-256, créditos por bytes, caché limitada,
prioridades y generaciones. No hay que preparar nuevamente los almacenes existentes.

El formato diferencial **XOR_RUNS_1** es un formato propio de esta implementación,
no una implementación de VCDIFF ni una compresión aproximada. La firma solo
propone bases; todos los píxeles finales se reconstruyen y verifican por SHA-256.
Las mediciones sistemáticas y la evaluación amplia de concurrencia pertenecen
a etapa 7. El funcionamiento demostrado no garantiza el mismo ahorro en toda imagen.

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

## Mensajes actuales PRIB de desarrollo versión 1

El catálogo anuncia `modes: "FULL,REUSE,REF,DELTA"`, `deltaCodec: "XOR_RUNS_1"`
y `maxRecoveries: 2`. El servidor y el frontend deben actualizarse juntos.
Los controles entrantes siguen siendo JSON plano. Se admiten hasta 32 campos,
con el límite previo de 8 KiB por control y 4096 bytes por cabecera binaria.

| Mensaje o campo | Dirección | Contrato |
| --- | --- | --- |
| CACHE_STATE PUT `similarity` | C → S | Firma hexadecimal de 15 dígitos del RGB retenido |
| BLOCK_DELTA | S → C | `mode=DELTA`, `codec=XOR_RUNS_1`, `baseImageId`, `baseId`, `baseHash`, `expectedHash` y payload |
| BLOCK_REF `baseHash` | S → C | Hash del RGB de la base, además de la identidad usada en etapa 5 |
| RECOVER | C → S | `viewId`, `transferId`, `reason` de un intento pendiente |
| RECOVERY_ACCEPTED | S → C | `viewId`, `transferId` fallido, `blockId`, `reason`, `attempt` y `mode=FULL` |
| VIEW_DONE | S → C | Objetivos, intentos, modos, bytes, recuperaciones y coste de selección diferencial |

PUT sin firma todavía permite coincidencia exacta REUSE/REF, pero no aporta esa
entrada al índice DELTA. El frontend actual calcula y anuncia siempre la firma
al conservar RGB. Todos los mensajes conservan versión, tipo y sesión. La cabecera
binaria conserva geometría, vista, transferencia y longitud de payload de etapas anteriores.

VIEW_DONE ahora requiere que todos los objetivos tengan ACK, además de terminar
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

## Pruebas y demostración reproducible

```powershell
./scripts/test.ps1
# Opcional, requiere Node.js; usa los vectores que generó la suite Java.
node ./scripts/test-delta.cjs
node ./scripts/test-cache.cjs
```

La suite Java genera un almacén de 512 × 128 con cuatro bloques: base aleatoria,
copia idéntica, copia con dos píxeles cambiados y contenido aleatorio distinto.
Solicita regiones de un bloque para demostrar FULL → REF → DELTA → REUSE → FULL.
Informa la carpeta temporal `build/delta-test-.../data` al terminar.

Para probar ese mismo caso en Chrome, iniciar un servidor sobre la carpeta informada:

```powershell
./scripts/servidor.ps1 -Port 8081 -Data 'build/delta-test-REEMPLAZAR/data'
```

En otra terminal, configurar Playwright como indica [pruebas-navegador.md](pruebas-navegador.md):

```powershell
$env:PRIB_URL = 'http://localhost:8081'
node ./scripts/browser/delta.cjs
```

El script exige la fixture 512 × 128 y `build/delta-expected.json` de la misma
suite; no usa una imagen gigante cualquiera. Inyecta los cuatro fallos únicamente
en el proceso de Chrome de prueba, comprueba hashes originales y continuidad de
sesión y deja capturas DELTA y de recuperación en `build/browser-check/`.
El visor de producción no tiene un interruptor para corromper datos.

Para explorar el catálogo habitual basta `./scripts/servidor.ps1`. Mostrar
transferencia expone FULL/REUSE/REF/DELTA y recuperaciones. DELTA depende del contenido
y de las bases conservadas; su presencia no está garantizada en cada región.
No se necesita internet para ejecutar el proyecto con el JDK y las imágenes disponibles.

## Evidencia del 3 de octubre de 2026

Las pruebas se ejecutaron en Windows con JDK 21.0.11 y heap máximo de 256 MiB.
Pasaron las suites anteriores y las de etapa 6: 100 diferencias dispersas exactas,
bordes, tramos inválidos, política de ahorro, límites del índice, cuatro modos por
WebSocket, cuatro causas de recuperación, pausa por crédito, generaciones y límite
de reintentos. Los vectores Java/JS produjeron RGB y firmas idénticos.

Chrome reconstruyó el objetivo sintético y comprobó las cuatro recuperaciones
reales del frontend, con SHA-256 exacto, conexión activa, capacidad recuperada y
protecciones liberadas. En la prueba de un bloque:

| Resultado | FULL | DELTA |
| --- | --- | --- |
| RGB reconstruido | 49152 bytes | 49152 bytes |
| Payload | 49152 bytes | 30 bytes |
| Mensaje binario PRIB | 49528 bytes | 562 bytes |

La suite habitual de Chrome también pasó con la imagen de 17 GB: dos clientes,
navegación rápida, zoom, bordes, panel, pantalla completa, 496 bloques verificados
en 4K, pausa/reanudación de créditos y reconexión.

El mensaje DELTA fue aproximadamente **98,9 % menor** en este caso sintético.
La medición incluye referencia y hashes, pero excluye controles JSON y cabeceras
WebSocket/TCP/IP. No representa el ahorro agregado de la imagen de 17 GB.
Las capturas y resultados quedan en `build/`, fuera de Git.

## Archivos principales

| Archivo | Responsabilidad |
| --- | --- |
| `DeltaCodec.java` | Encoder/decoder diferencial y márgenes configurables |
| `SimilarityIndex.java` | Firma, buckets e instantáneas con candidatos limitados |
| `ClientCache.java` | Inventario, índice exacto, similitud e invalidación |
| `PribSession.java` | Cuatro modos, coste completo, ACK, RECOVER y generaciones |
| `web/delta.js` | Firma, reconstrucción y errores semánticos del navegador |
| `web/cache.js`, `web/app.js` | Inventario protegido, recuperación y métricas |
| `DeltaCodecTest.java`, `DeltaProtocolTest.java` | Exactitud, límites e integración real |
| `scripts/test-delta.cjs`, `scripts/browser/delta.cjs` | Interoperabilidad y Chrome con fallos inyectados |

El diseño sigue la propuesta revisada del equipo. XOR_RUNS_1, la firma de medias,
los límites y los márgenes son decisiones explícitas de esta implementación.
La siguiente etapa medirá ahorro neto, latencias, memoria y concurrencia con las
imágenes grandes y ajustará las políticas usando esas mediciones.
