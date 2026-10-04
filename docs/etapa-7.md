# Etapa 7: evaluación del sistema completo

Implementada y ejecutada el **3 de octubre de 2026**, hora de Guatemala
(el JSON registra UTC del 4 de octubre). Se probaron las seis imágenes locales,
con máximo demostrado de **75471 × 75471**, la imagen del curso denominada 17 GB.
Sus píxeles RGB del nivel original equivalen a 17087615523 bytes. No se ha
preparado ni validado la imagen de 28 GB; tampoco se afirma una prueba de 24 GB.

## Evidencia reproducible

- [Mediciones completas JSON](mediciones/etapa-7-2026-10-03.json): entorno, cada vista, modos, controles, estadísticas del servidor, muestras de memoria y SHA-256 de las fuentes evaluadas.
- [Mediciones CSV](mediciones/etapa-7-2026-10-03.csv): tabla de vistas para analizar fuera del visor.
- [Captura de detalle al 400 %](evidencias/etapa-7-detalle.png): esquina inferior derecha del nivel original, números legibles y vista completa.

Entorno: Windows 10, Ryzen 5 3550H, 8 procesadores lógicos, aproximadamente
6 GB de RAM disponible como memoria física total del equipo, JDK 21.0.11,
Node 24.19.0 y Chrome 154.0.8037.98. Ventana de navegador de 1440 × 1000.
Servidor aislado en puerto libre, cuatro trabajadores y `-Xmx256m`.
Se utilizaron las dependencias ya instaladas, sin descargar imágenes ni librerías.

## Cómo repetir

Desde la raíz del proyecto, con los almacenes completos en `data/`:

```powershell
./scripts/test.ps1
node scripts/test-cache.cjs
node scripts/test-delta.cjs
./scripts/evaluar.ps1
```

La evaluación opcional requiere **Node, Chrome y Playwright locales**. No son
requisitos para ejecutar el visor. Si Playwright está fuera de la resolución
habitual de Node, configurar `$env:PRIB_PLAYWRIGHT` con la ruta local del paquete.
Se puede usar `$env:PRIB_BROWSER_PATH` para un Chromium instalado compatible,
`$env:PRIB_JAVA` para el ejecutable Java y `$env:PRIB_DATA` para otro catálogo.
En Windows, `$env:PRIB_POWERSHELL` permite indicar el ejecutable del muestreador.
La prueba de crédito necesita una imagen que requiera más de 256 KiB para una
vista nueva; usar el catálogo de las seis imágenes para reproducir esta evidencia.

El script compila, crea su propio servidor, abre Chrome sin interfaz visible y
los cierra al terminar o fallar. No modifica los almacenes ni detiene servidores
existentes. Guarda nuevas mediciones en `build/evaluation-stage7.json` y `.csv`,
y una captura en `build/browser-check/etapa-7-detalle.png`. No reemplaza la evidencia
versionada. Una aserción fallida devuelve código distinto de cero.

## Qué se mide

Se solicitan origen, centro y borde del **nivel 0** de cada imagen, primero con
inventario del cliente vacío y después repitiendo la misma región. Vaciar el
inventario no borra la caché de disco del sistema operativo: estos son ensayos de
caché de cliente fría, no de arranque de disco frío. Las siguientes vistas exploran
40 regiones deterministas de la imagen mayor y 16 vistas distribuidas entre cuatro
clientes simultáneos. Todas terminan con bloques esperados/verificados iguales,
SHA-256 correcto y protecciones de generación liberadas.

La latencia abarca la invocación desde Playwright, solicitud, planificación,
lecturas, transmisión, reconstrucción, SHA-256 y procesamiento de `VIEW_DONE`.
Incluye el coste del controlador de pruebas y del navegador; no es RTT ni tiempo
exclusivo del servidor. p50/p95 usan el rango más cercano, sin interpolación. Con
solo seis muestras, el p95 coincide con el máximo; no es una garantía de servicio.
La segunda ejecución produjo los resultados siguientes; hubo variación respecto
a la primera, por lo que no se presentan como valores constantes del sistema.

**Bytes PRIB** incluyen uint32 de longitud, JSON de cabecera y payload binario.
El **FULL equivalente** conserva IDs, hash y geometría de cada transmisión,
quita la referencia a base y usa `BLOCK_FULL`, `RAW`, `FULL` y RGB8 completo.
Ahorro = `1 - bytesBinarios / bytesFullEquivalentes`. Incluye reintentos cuando
existen; no es un experimento de otra política ni una comparación con PNG/JPEG.
Los controles JSON entrantes y salientes se miden por separado. Se excluyen
HTTP inicial, framing WebSocket, TCP/IP y tráfico ajeno al visor. Por tanto,
el porcentaje es ahorro del canal binario PRIB, no ahorro total de red.

## Resultados

| Escenario | Vistas | p50 ms | p95 ms | Bytes PRIB | Ahorro frente a FULL |
| --- | --- | --- | --- | --- | --- |
| fria-origen | 6 | 931 | 2398 | 16069542 | 18.22 % |
| repetida-origen | 6 | 144 | 173 | 212747 | 98.92 % |
| fria-centro | 6 | 913 | 2435 | 18084517 | 19.91 % |
| repetida-centro | 6 | 163 | 198 | 251631 | 98.89 % |
| fria-borde | 6 | 733 | 1412 | 15392125 | 20.66 % |
| repetida-borde | 6 | 179 | 265 | 242762 | 98.75 % |
| navegacion-40 | 40 | 830 | 946 | 121389918 | 23.26 % |
| concurrencia-4 | 16 | 1456 | 2346 | 47775459 | 23.46 % |
| otros-con-cliente-pausado | 3 | 1036 | 1065 | 10109790 | 19.02 % |
| recuperacion-hash | 1 | 750 | 750 | 2841894 | 21.42 % |
| detalle-400 | 1 | 1010 | 1010 | 256221 | 11.84 % |

Las 18 vistas con inventario vacío ahorraron **19,61 %** en conjunto. Las 18
repeticiones ahorraron **98,85 %**: 707140 bytes frente a 61631043 bytes FULL
equivalentes. La navegación de 40 regiones ahorró aproximadamente **23,26 %**.
REF y DELTA también aparecieron en las imágenes reales, además de REUSE y FULL.

| Modo | Transmisiones | Bytes PRIB |
| --- | --- | --- |
| FULL | 2313 | 111584901 |
| REUSE | 1323 | 707140 |
| REF | 21 | 11248 |
| DELTA | 4073 | 131023878 |

Estos totales incluyen vistas iniciales, vistas interrumpidas, las pausas y la
reconexión; no coinciden con la suma de las filas de vistas medidas. El JSON
conserva los bytes y conteos por modo de cada vista, y los `deltaCandidates` y
`deltaNanos` de `VIEW_DONE`. El tiempo diferencial incluye firmas, lectura de
bases y codificación; no debe interpretarse como CPU exclusiva del encoder.

## Recursos y almacenamiento

| Imagen | Dimensiones | Niveles | Archivos | Bytes en disco |
| --- | --- | --- | --- | --- |
| imagen-100mb | 5775 × 5775 | 7 | 16 | 24841360 |
| imagen-17gb | 75471 × 75471 | 11 | 24 | 3987264981 |
| imagen-1890 | 1890 × 1890 | 5 | 12 | 2731133 |
| imagen-4gb | 36743 × 36743 | 10 | 22 | 979003879 |
| imagen-500mb | 12900 × 12900 | 8 | 18 | 124310114 |
| imagen-782 | 782 × 782 | 4 | 10 | 503061 |

El espacio suma todos los archivos del almacén, incluido `result.txt`; por eso
supera ligeramente los números de preparación de etapa 2, que excluían ese reporte.
La prueba lee bloques desde packs existentes, sin generar temporales de imagen
completa ni recalcular la pirámide durante la navegación.

- Caché RGB máxima observada: **16761891 bytes**, por debajo de 16 MiB; máximo observado de 348 entradas, dentro del límite de 1024. Son máximos por cliente, no la suma de cuatro clientes.
- Heap JavaScript observado mediante `performance.memory`: aproximadamente **41,6 MiB**. Es una lectura aproximada que puede compartir proceso entre páginas; no representa toda la memoria de Chrome ni un límite de heap.
- Máximo residente observado del servidor: **149057536 bytes (142,15 MiB)**. Máximo de memoria privada: **224313344 bytes (213,92 MiB)**.
- Se registraron **148 muestras** del proceso Java cada aproximadamente 500 ms. Los picos entre muestras pueden perderse. Working set y private bytes incluyen la JVM; no son mediciones de heap. El límite de heap sigue siendo 256 MiB.

Los límites del código siguen vigentes: 32 conexiones, cuatro trabajos activos,
cola de 32 trabajos, un trabajo por sesión, salida de 1 MiB/128 elementos por
cliente, hasta 558 bloques por vista, inventario de 16 MiB/1024 entradas y cuatro
candidatos diferenciales. La evaluación demuestra cuatro clientes; no demuestra
rendimiento con el máximo de 32 conexiones ni ausencia de fugas en ejecución infinita.

## Créditos, cancelaciones y recuperación

Un cliente dejó de devolver créditos y alcanzó `WAIT_CREDIT`. Sus bytes recibidos
permanecieron constantes mientras otros tres completaron regiones nuevas. Al
reanudar las devoluciones terminó la vista y el pendiente volvió a cero. Los
14316 estados de crédito observados respetaron saldo y deuda entre cero y la
capacidad negociada. No se simuló latencia de una red WAN: la lentitud se modeló
reteniendo las concesiones del cliente.

Se solicitaron 32 cambios rápidos de región. La generación final terminó con
SHA-256 correcto y sin protecciones retenidas. En toda la ejecución se recibieron
131 `CANCELLED`, con 2503 tareas canceladas acumuladas y cero bytes preparados
sin enviar reportados. Las tareas pueden corresponder a regiones superpuestas;
no son bloques únicos ni una estimación de ahorro de red. Los datos ya enviados
no se cuentan como ahorro y siguen consumiendo crédito hasta procesarse.

Se reconectó un cliente con deuda pendiente: obtuvo sesión nueva, continuó la
región y terminó sin deuda. El servidor de las otras pestañas permaneció activo.

Para probar recuperación en la imagen grande se alteró una copia del primer
payload FULL en el receptor de prueba, antes de SHA-256. Hubo exactamente un
`RECOVER/HASH_MISMATCH`; la vista final verificó **72 bloques en 73 transmisiones**,
con una recuperación selectiva y conexión activa. No se dañó el almacén ni se
modificó la lógica del visor. Las cuatro causas y el límite de dos reintentos
siguen cubiertos por las pruebas Java/JavaScript y la fixture Chrome de etapa 6.

## Decisión sobre políticas

Las mediciones sostienen mantener la caché de 16 MiB, los cuatro trabajadores y
la selección diferencial con ahorro mínimo de 15 % y 256 bytes. El cliente
repetido ya obtiene casi 99 % de ahorro; las regiones nuevas obtienen DELTA sin
cargar imágenes completas, y los clientes activos progresan con otro detenido.
No hay evidencia en estos ensayos para aumentar memoria, concurrencia o reducir
el margen de DELTA. Se conservan también los límites de candidatos y reintentos.
Cambiar parámetros requiere repetir esta misma carga y comparar latencias,
bytes y memoria; el reporte no atribuye mejoras a ajustes que no se ejecutaron.

La captura de la esquina inferior derecha al 400 % se inspeccionó visualmente:
los números son legibles y la vista no contiene huecos de bloques. Esta evidencia
cierra etapa 7 para el máximo disponible y demostrado de 17 GB. Etapa 8 queda
pendiente para consolidar el documento del protocolo y preparar la demostración.
