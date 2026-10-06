# Pruebas y resultados

Ejecutar los comandos desde PowerShell en la raíz del repositorio.
Para instalar requisitos y preparar imágenes, consultar la [guía del equipo](preparacion-equipo.md).
Las pruebas de Java no requieren Node ni los ZIP del curso. Las de navegador son opcionales.

- [Prueba manual del visor](#prueba-manual-del-visor)
- [Consulta desde PowerShell](#consultar-bloques-desde-powershell)
- [Pruebas automáticas](#pruebas-automáticas)
- [Navegador](#pruebas-de-navegador) y [cuatro modos](#demostrar-los-cuatro-modos-y-fallos)
- [Evaluación completa](#evaluación-completa) y [resultados](#resultados-del-5-de-octubre-de-2026)

## Prueba manual del visor

1. Ejecutar `./scripts/servidor.ps1` y abrir http://localhost:8080. Mantener la terminal abierta.
2. Seleccionar una imagen preparada, acercar al 400 %, arrastrar y visitar centro y bordes.
3. Abrir **Mostrar transferencia**: esperar **Vista completa**, bloques esperados/verificados coincidentes y **SHA-256 correcto**.
4. Sin cambiar coordenadas, pulsar **Ir a la región**: debe aparecer REUSE si los bloques siguen en caché, con menos bytes PRIB.
5. Probar **Ver imagen completa**, pantalla completa y redimensionar la ventana.
6. Abrir otra pestaña: debe tener otra sesión y permitir navegar de forma independiente.

FULL, REF y DELTA dependen del contenido y las bases disponibles; no tienen que
aparecer todos en cada vista. La prueba sintética de abajo demuestra los cuatro.

Para probar créditos, elegir una imagen grande al 100 % y pulsar **Pausar
devoluciones** antes de desplazarse a una región nueva. Debe alcanzar **Esperando
crédito** mientras otra pestaña continúa. Con mucho zoom o REUSE puede caber toda
la vista y no detenerse. **Reanudar devoluciones** completa la última vista y
devuelve la deuda a cero. **Reconectar** crea una sesión nueva y conserva la región.
Pausar retiene concesiones, no buffers ni ACK. Para terminar, usar Ctrl+C en el servidor.

## Consultar bloques desde PowerShell

Abre PowerShell en la carpeta raíz del repositorio, donde están `README.md`,
`scripts/` e `imagenes/`. Si la terminal está en otro lugar, usa
`Set-Location 'C:\ruta\a\CC8-Proyecto2'`, sustituyendo esa ruta por la de tu copia.
Los scripts resuelven las rutas relativas desde la raíz del proyecto.

**La imagen de entrada y la carpeta de resultados son cosas distintas.**
`data/prueba-manual` es un nombre de carpeta elegido por nosotros, no el nombre
de un PNG. Puedes cambiarlo; luego debes usar ese mismo nombre al consultar.
Las comillas permiten escribir rutas o nombres que contienen espacios.

### 1. Preparar una imagen

```powershell
./scripts/preparar.ps1 -Output 'data/prueba-manual'
```

Este comando compila Java, busca el ZIP pequeño en `imagenes/` y prepara por
defecto `000-001-800-5105.png`, de 782 × 782 píxeles. Crea la carpeta
`data/prueba-manual` con cuatro niveles: 782, 391, 196 y 98 píxeles por lado.

| Parámetro de preparar.ps1 | Qué debes indicar | Si lo omites |
| --- | --- | --- |
| `-Zip` | Ruta de un ZIP existente que contiene la imagen | Busca un ZIP cuyo nombre contenga `Peque` en `imagenes/` |
| `-Entry` | Nombre exacto del PNG dentro del ZIP, incluyendo su carpeta interna si existe | Usa `000-001-800-5105.png` |
| `-Output` | Carpeta nueva donde se guardará el almacén; eliges el nombre | Usa `data/imagen-782` |

Puedes seleccionar explícitamente otra imagen del ZIP pequeño:

```powershell
./scripts/preparar.ps1 -Entry '000-010-700-24119.png' -Output 'data/prueba-1890-manual'
```

Aquí el nombre de `-Entry` debe existir dentro del ZIP. En cambio,
`prueba-1890-manual` es un nombre libre para la carpeta de resultados.
Para seleccionar un ZIP diferente, se usa esta misma forma:

```powershell
# Ejemplo con la imagen de 17 GB: puede tardar varios minutos.
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-28G-17G.zip' -Entry '017-110-000-24650032.png' -Output 'data/prueba-17gb-manual'
```

**Solo necesitas preparar cada imagen una vez.** Para repetir la preparación,
usa otra carpeta de salida. Si ya tienes un almacén preparado, pasa directamente
a la consulta y utiliza su ruta en `-Store`.

### 2. Consultar una región de la imagen preparada

Después de ejecutar el primer ejemplo:

```powershell
./scripts/consultar.ps1 -Store 'data/prueba-manual' -Level 0 -X 0 -Y 0 -Width 256 -Height 256 -Output 'output/region-manual.png'
```

| Parámetro de consultar.ps1 | Significado | Valor predeterminado |
| --- | --- | --- |
| `-Store` | Carpeta existente generada por `preparar.ps1`; no es el ZIP ni el PNG original | `data/imagen-782` |
| `-Level` | Nivel de resolución: 0 conserva el detalle original | `0` |
| `-X` | Columna en píxeles donde empieza la región, desde la izquierda | `0` |
| `-Y` | Fila en píxeles donde empieza la región, desde arriba | `0` |
| `-Width` | Ancho en píxeles de la región que se extraerá | `256` |
| `-Height` | Alto en píxeles de la región que se extraerá | `256` |
| `-Output` | Nombre nuevo del archivo PNG que se creará; eliges el nombre | `output/region.png` |

**`-Store` debe coincidir con el `-Output` utilizado al preparar la imagen.**
En preparación, `-Output` es una carpeta; en consulta, es un archivo PNG.
El ejemplo extrae los primeros 256 × 256 píxeles del nivel original. Debe mostrar
`Región verificada y exportada`. Para abrir el resultado en el visor de Windows:

```powershell
Invoke-Item './output/region-manual.png'
```

Puedes cambiar `-X` y `-Y` para explorar otras zonas. Las coordenadas pertenecen
al nivel elegido: `X + Width` y `Y + Height` no deben superar sus dimensiones.
Cada consulta permite hasta 4,194,304 píxeles. Usa otro nombre de PNG para cada
resultado que quieras conservar; el script no sobrescribe archivos existentes.

### 3. Consultar la miniatura y revisar el reporte

La imagen del primer ejemplo tiene estos niveles:

| Nivel | Dimensiones |
| --- | --- |
| 0 | 782 × 782, original |
| 1 | 391 × 391 |
| 2 | 196 × 196 |
| 3 | 98 × 98, miniatura |

```powershell
./scripts/consultar.ps1 -Store 'data/prueba-manual' -Level 3 -Width 98 -Height 98 -Output 'output/miniatura-manual.png'
Invoke-Item './output/miniatura-manual.png'
Get-Content './data/prueba-manual/result.txt'
Get-Content './data/prueba-manual/image.properties'
```

Al omitir `-X` y `-Y`, ambos valen 0. La consulta extrae todo el nivel 3.
**Estos tamaños corresponden a la imagen de 782 × 782**; otras imágenes tienen
niveles distintos. Sus dimensiones aparecen en `image.properties` como
`level.N.width` y `level.N.height`. `result.txt` resume tiempo, espacio y heap.

Las consultas leen solo los bloques necesarios, verifican sus hashes y no
necesitan volver a abrir el ZIP. Cada nivel tiene un archivo `.pack` de datos y
un `.idx` para localizar los bloques. Si una preparación falla, `INCOMPLETE`
impide usar el almacén parcial. Los detalles están en [protocolo](protocolo.md).

### Qué se puede limpiar

| Carpeta | Contenido | Efecto de vaciarla |
| --- | --- | --- |
| `output/` | Resultados de etapa 1 y PNG exportados en las consultas de ejemplo | Se pierden esas pruebas y vistas; se pueden generar otra vez |
| `data/` | Almacenes preparados, índices, niveles y reportes de etapa 2 | Habría que volver a preparar las imágenes para consultarlas |
| `imagenes/` | ZIP originales del curso | Habría que recuperarlos para preparar nuevas imágenes |

Con las rutas de estos ejemplos, limpiar `output/` no modifica código, ZIP ni
almacenes en `data/`. Si elegiste guardar un almacén con `-Output output/...`,
ese almacén también se perdería al vaciar `output/`. Revisa siempre dónde
has guardado tus resultados. Tras limpiar, puedes reutilizar los nombres de PNG
eliminados; las carpetas de preparación que sigan en `data/` continúan existiendo.




## Pruebas automáticas

El catálogo local se redujo a 4, 17, 28 y 55 GB. Las mediciones de ocho imágenes
conservadas al final son históricas. Las pruebas Java generan imágenes propias en build/.

```powershell
./scripts/test.ps1
# Opcional: Node.js, sin paquetes adicionales; ejecutar después de la suite Java.
node ./scripts/test-cache.cjs
node ./scripts/test-delta.cjs
```

Deben terminar con mensajes PASS y sin errores. Cubren filtros/CRC de PNG,
pirámides y bordes, comparación independiente de píxeles, corrupción, créditos,
caché, prioridades, generaciones, los cuatro modos y recuperación selectiva.
Las fixtures se generan en build/. Las comparaciones completas con ImageIO se
limitan a imágenes pequeñas; las grandes se verifican por muestras y regiones.

Para comprobar un almacén existente en todos sus niveles después de compilar las pruebas:

```powershell
java -Xmx256m -cp build/classes prib.ImageStoreTest data/imagen-28gb
java -Xmx256m -cp build/classes prib.ImageStoreTest data/imagen-55gb
```

Deben indicar PASS para esquinas y centro en 11 y 12 niveles, respectivamente.
No vuelven a preparar ni modificar los almacenes.

## Pruebas de navegador

La preparación web tiene una prueba aislada, después de `./scripts/test.ps1`
y de configurar Playwright como se explica abajo:

```powershell
node ./scripts/browser/preparation.cjs
```

Inicia su propio servidor con un ZIP sintético y destinos en build/. Comprueba
catálogo vacío, formulario, dos pestañas, cierre del panel, actualización sin reinicio,
errores de destino/PNG y reconexión. No modifica las cuatro imágenes del equipo.
ImagePreparationTest también verifica rutas restringidas, tarea única y no sobrescritura.
Ambas pruebas pasaron al incorporar la preparación web; el panel también se inspeccionó en Chrome.
Después pasó el evaluador completo con el catálogo actual de cuatro imágenes:
85 vistas medidas, cuatro clientes, créditos, cancelación, reconexión y recuperación
selectiva, sin errores. El reporte de esa comprobación queda en
`build/evaluation-stage7.json` y `.csv`; se conserva el JSON histórico de ocho
imágenes como referencia de las cifras publicadas abajo.
La suite habitual run.cjs descrita abajo requiere además una imagen pequeña preparada;
se puede generar en un catálogo de prueba separado para conservar el catálogo de cuatro.

### Preparación

Se necesita Node.js, Google Chrome instalado y Playwright. La suite fue
verificada con Playwright 1.62.1. Desde la raíz del repositorio, instalar la
herramienta una vez (requiere internet):

```powershell
npm install --prefix build/browser-tools --no-save --package-lock=false playwright@1.62.1
```

La dependencia queda en `build/`, fuera de Git. Si se limpia esa carpeta,
hay que instalarla otra vez; los scripts de prueba sí permanecen en el repositorio.
No se descarga otro navegador: se utiliza Chrome instalado.

Estas pruebas usan la imagen del curso de **17 GB, de 75471 × 75471 píxeles**,
preparada en `data/imagen-17gb`, además de una imagen pequeña preparada.
Las coordenadas de prueba corresponden a esa imagen, no a un PNG cualquiera.
Consultar el [README](../README.md) si aún no están preparadas.

### Ejecución

En una terminal, dejar el servidor activo:

```powershell
./scripts/servidor.ps1
```

En otra terminal, desde la raíz del repositorio:

```powershell
$env:PRIB_PLAYWRIGHT = (Resolve-Path 'build/browser-tools/node_modules/playwright').Path
node ./scripts/browser/run.cjs
```

Deben aparecer mensajes `PASS` y finalizar sin errores. Las pruebas abren
navegadores sin ventana, los cierran al terminar y dejan capturas en
`build/browser-check/`. No modifican los almacenes ni detienen el servidor.

| Script | Qué comprueba |
| --- | --- |
| `sessions.cjs` | Dos sesiones, navegación rápida, bordes y reconexión |
| `zoom.cjs` | Acercamiento, región solicitada, anclaje al cursor y arrastre proporcional |
| `responsive.cjs` | Panel, pantalla completa, ventanas estrechas y tamaño 4K |
| `credits.cjs` | Pausa, reanudación, concesiones repetidas y sesión nueva sin deuda |
| `reuse.cjs` | Repetición con REUSE, bytes evitados, generaciones, caché y reconexión |

También se puede ejecutar uno: `node ./scripts/browser/credits.cjs`.
La suite se detiene en el primer fallo y devuelve un código de salida distinto de cero.

### Configuración opcional

```powershell
$env:PRIB_URL = 'http://localhost:8081'
$env:PRIB_IMAGE = 'nombre-de-la-carpeta-de-la-imagen-17gb'
```

`PRIB_URL` debe coincidir con el servidor iniciado. `PRIB_IMAGE` cambia el nombre
mostrado en el catálogo, no la imagen ni las dimensiones requeridas por las pruebas.
Si Chrome está en una ubicación distinta, `PRIB_BROWSER_PATH` permite indicar
la ruta completa a un ejecutable compatible. `PRIB_PLAYWRIGHT` puede apuntar a
otra instalación existente de Playwright. Ningún script contiene rutas personales.



reuse.cjs puede ejecutarse por separado con una imagen pequeña. Para demostrar
REF entre imágenes, usar dos almacenes pequeños idénticos y PRIB_REF_IMAGE con
el nombre del segundo. No es necesario duplicar un almacén gigante.

## Demostrar los cuatro modos y fallos

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

En otra terminal, configurar Playwright como indica la sección de [pruebas de navegador](#pruebas-de-navegador):

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

## Evaluación completa

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
vista nueva; usar el catálogo de ocho imágenes para reproducir las mediciones conservadas.

El script compila, crea su propio servidor, abre Chrome sin interfaz visible y
los cierra al terminar o fallar. No modifica los almacenes ni detiene servidores
existentes. Guarda nuevas mediciones en `build/evaluation-stage7.json` y `.csv`,
y una captura en `build/browser-check/etapa-7-detalle.png`. No reemplaza la evidencia
versionada. Una aserción fallida devuelve código distinto de cero.

### Interpretar las mediciones

La latencia incluye Playwright, petición, lecturas, transmisión, reconstrucción,
SHA-256 y VIEW_DONE; no es RTT ni CPU exclusiva del servidor. p50/p95 usan el rango
más cercano sin interpolación. Caché fría significa inventario de cliente vacío,
no vaciar la caché de disco del sistema operativo.

FULL equivalente conserva identidad, hash y geometría de cada transmisión,
elimina la referencia a base y utiliza RGB completo. El ahorro es
`1 - bytesBinarios / bytesFullEquivalentes`. Incluye reintentos cuando existen.
Controles entrantes y salientes se miden por separado; no se incluye HTTP inicial
ni framing WebSocket/TCP/IP. No es una comparación con PNG/JPEG ni ahorro total de red.
Los totales por modo incluyen vistas iniciales y canceladas, por lo que pueden
ser diferentes de la suma de las vistas completas medidas.

## Resultados del 5 de octubre de 2026

Pasaron `./scripts/test.ps1`, `node scripts/test-cache.cjs` y
`node scripts/test-delta.cjs`. Después de terminar ambas preparaciones se ejecutó
el evaluador completo, con servidor aislado y Chrome local:

- Origen, centro y borde de cada una de las ocho imágenes; primera visita y repetición.
- Cuarenta regiones de navegación en la imagen de 55 GB y dieciséis vistas entre cuatro clientes simultáneos.
- Cliente sin devolver créditos: se detuvo mientras los otros tres progresaban; al reanudar terminó sin deuda.
- Treinta y dos cambios rápidos de región, cancelación de trabajo anterior y reconexión con deuda pendiente.
- Corrupción de una copia del payload recibido: un `RECOVER/HASH_MISMATCH`, 72 bloques verificados en 73 transmisiones. No se alteró el almacén.
- Detalle al 400 % de la imagen de 55 GB, con números visibles y vista completa.

Las **109 vistas medidas** terminaron con bloques esperados y verificados
coincidentes, sin errores y sin bases retenidas por una generación terminada.
Los cuatro modos aparecieron durante la evaluación: FULL, REUSE, REF y DELTA.

| Medición | Resultado |
| --- | --- |
| 28 GB: primera visita a origen/centro/borde | 594 / 780 / 629 ms |
| 55 GB: primera visita a origen/centro/borde | 612 / 659 / 547 ms |
| 55 GB: navegación, p50 / p95 | 647 / 714 ms |
| 55 GB: cuatro clientes, p50 / p95 | 1386 / 2164 ms |
| Caché RGB máxima observada por cliente | 16746315 bytes, inferior a 16 MiB |
| Máximo observado de entradas de caché | 359, límite 1024 |
| Memoria residente máxima observada del servidor | 206520320 bytes, aproximadamente 197 MiB |
| Memoria privada máxima observada del servidor | 320614400 bytes, aproximadamente 306 MiB |

En las 40 regiones de navegación se transmitieron 124211139 bytes PRIB frente
a 161513743 bytes FULL equivalentes: aproximadamente **23,1 % de ahorro**.
En las 24 vistas repetidas, el ahorro agregado fue aproximadamente **98,85 %**.
Son comparaciones de mensajes PRIB completos contra FULL equivalente; excluyen
framing WebSocket/TCP/HTTP y no comparan contra otro códec de imágenes.

Entorno: Windows, Intel i7-1065G7, 8 procesadores lógicos, aproximadamente 11,7 GiB
de RAM utilizable, Java 21.0.11 y Chrome 154.0.8037.93. Servidor con heap de
256 MiB y cuatro trabajadores; navegador de 1440 × 1000. Las 133 muestras del
proceso Java se tomaron aproximadamente cada 500 ms: pueden perder picos entre
muestras y no representan el heap. No se cambió ninguna política del protocolo.

### Preparación de 28 y 55 GB

Se procesaron los ZIP secuencialmente, sin extraer el PNG completo, con Java 21
y `-Xmx256m`. Los almacenes anteriores se conservaron.

| Imagen | Dimensiones | Niveles | Tiempo | Almacén generado | Heap observado |
| --- | --- | --- | --- | --- | --- |
| 28 GB | 96922 × 96922 | 11 | 550,895 s (9 min 11 s) | 6618239320 bytes | 200,83 MiB |
| 55 GB | 136325 × 136325 | 12 | 1052,434 s (17 min 32 s) | 13084950020 bytes | 213,56 MiB |

El tamaño del almacén incluye packs, índices y manifiesto, pero excluye
`result.txt`. El heap es un muestreo durante preparación, no el máximo exacto
ni la memoria total del proceso. Ambos procesos terminaron sin errores.
Se comprobaron mediante SHA-256 esquinas y centro en todos los niveles.



## Mediciones conservadas y límites

Se conserva únicamente el [JSON de la evaluación histórica](mediciones/etapa-7-2026-10-05.json):
109 vistas de ocho imágenes, entorno, recursos, modos, contadores y hashes del código.
Los CSV y capturas pueden regenerarse con el evaluador en build/; no se versionan.
No se han vuelto a ejecutar las pruebas por esta reorganización documental;
las cifras describen la ejecución del 5 de octubre, no una ejecución nueva.

La preparación recorrió los PNG completos. La comprobación posterior y navegación
verificaron regiones muestreadas, no cada píxel mediante un decodificador independiente.
Las pruebas son locales: no demuestran una red WAN, 32 clientes ni ejecución indefinida.
No se probó la imagen de 93 GB. El máximo demostrado es 136325 × 136325 (55 GB).
Se mantienen las políticas actuales y la etapa 8 sigue pendiente.
