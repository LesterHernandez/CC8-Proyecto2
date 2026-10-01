# PRIB — Protocolo de Reutilización Inteligente de Bloques

Proyecto de Ciencias de la Computación VIII para servir imágenes de ultra alta
resolución mediante un servidor asíncrono en Java. PRIB decidirá qué bloques
necesita cada cliente, cuáles puede reutilizar y cuándo transmitirlos según
sus créditos de recepción. La propuesta incluye REUSE, REF, DELTA y FULL.

**Estado actual: etapas 1 y 2 implementadas, con preparación probada hasta 17 GB.** Tenemos lectura y
verificación de bloques, almacenamiento comprimido y niveles de resolución. Todavía no hay servidor HTTP/WebSocket ni interfaz web.
Consulta el [plan de desarrollo de ocho etapas](docs/plan-desarrollo.md) para
conocer el alcance y los criterios de cierre de cada avance.

## Pruebas manuales de etapa 2 en PowerShell

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
impide usar el almacén parcial. Los detalles están en [etapa-2.md](docs/etapa-2.md).

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

## Qué hace la etapa 1

La prueba abre un PNG directamente desde el ZIP y lo lee por filas. Reúne una
franja de hasta 128 filas y la divide en bloques de **128 × 128 píxeles**;
los bloques de los bordes conservan sus dimensiones reales, sin relleno.

Cada bloque se guarda como bytes RGB y se compara píxel por píxel con ImageIO,
el decodificador independiente de Java. También se calcula un hash SHA-256,
que permite verificar que los mismos bytes se interpretan correctamente en
JavaScript. Así comprobamos la fidelidad de los datos antes de implementar
la transmisión y la reconstrucción diferencial.

El lector incremental mantiene dos filas. ImageIO sí carga la imagen pequeña
completa como referencia de prueba: por eso esta ejecución admite hasta diez
millones de píxeles. **No usar esta prueba para las imágenes de 17 o 28 GB.**
Para preprocesar imágenes grandes se usa `preparar.ps1`, sin la referencia completa de ImageIO.

## Requisitos y preparación

- JDK 21: `java -version` y `javac -version` deben mostrar la versión 21.
- PowerShell para ejecutar los scripts.
- ZIP de imágenes pequeñas del curso dentro de `imagenes/`, sin extraerlo.
- Node.js solo si se desea ejecutar la verificación adicional en JavaScript.

No se necesitan Maven ni paquetes externos. Una vez instaladas las herramientas
y descargadas las imágenes, las pruebas funcionan sin internet. El script
principal limita el heap de Java a 256 MiB; esto no es toda la RAM del proceso.

## Repetir las pruebas de etapa 1

Abre una terminal PowerShell en la raíz del repositorio:

```powershell
./scripts/probar.ps1 -Output 'output/prueba-nueva'
./scripts/probar.ps1 -Entry '000-010-700-24119.png' -Output 'output/prueba-grande-nueva'
```

El script compila Java y luego ejecuta la prueba. `-Entry` selecciona el PNG
dentro del ZIP; al omitirlo se usa `000-001-800-5105.png`. `-Output` indica dónde
guardar los resultados. Usa una carpeta nueva en cada ejecución: no se
sobrescriben carpetas existentes ni se modifican los archivos originales.

| Prueba | Dimensiones | Bloques esperados |
| --- | --- | --- |
| Predeterminada | 782 × 782 | 49 |
| Segunda imagen | 1890 × 1890 | 225 |

La terminal debe mostrar `Píxeles: verificados contra ImageIO` y finalizar
sin errores. También muestra tiempo y memoria observada, que pueden variar
entre ejecuciones. El nombre `prueba-grande-nueva` se refiere solo a la segunda
imagen de prueba, no a las imágenes de varios gigabytes.

## Archivos que se generan

| Archivo | Para qué sirve |
| --- | --- |
| `0_0.rgb`, `1_0.rgb`, etc. | Bytes de cada bloque; el nombre indica columna y fila |
| `blocks.csv` | Posición, dimensiones, cantidad de bytes y SHA-256 por bloque |
| `result.txt` | Resumen de la prueba, tiempo y heap observado |

Los `.rgb` son datos crudos, no fotografías que se abran directamente en un visor.
Puedes consultar el manifiesto y el reporte desde la terminal:

```powershell
Get-Content ./output/prueba-nueva/blocks.csv -TotalCount 6
Get-Content ./output/prueba-nueva/result.txt
```

## Verificaciones adicionales

Las pruebas Java comprueban el almacén de etapa 2 (niveles, regiones, compresión y fallos) y generan una imagen sintética para verificar los cinco filtros
PNG, varios chunks IDAT, bordes parciales y rechazo de archivos dañados:

```powershell
./scripts/test.ps1
```

No necesitan los ZIP del curso. Deben aparecer `PASS filters`, `PASS crc` y
`PASS truncated`. Los dos últimos significan que se rechazó correctamente un
PNG con CRC incorrecto y otro incompleto. La evidencia se guarda en una carpeta
nueva `build/png-test-*`, excluida de Git.

Después de generar los bloques, puedes verificar su formato desde JavaScript:

```powershell
node ./scripts/verificar-bloques.cjs ./output/prueba-nueva
```

Debe indicar `PASS JavaScript: 49 bloques, longitudes, hashes y conversión RGB/RGBA`.

## Organización y avances

- `src/main/java/prib/Probe.java`: recorrido principal; recomendado para empezar a leer.
- `src/main/java/prib/PngRows.java`: lectura incremental, filtros y validación PNG.
- `src/main/java/prib/PrepareImage.java`: preparación incremental de niveles.
- `src/main/java/prib/ImageStore.java`: almacenamiento e índice por bloques.
- `src/main/java/prib/ViewRegion.java`: exportación regional a PNG.
- `src/test/java/prib/`: pruebas de lectura PNG, pirámides y acceso regional.
- `scripts/`: comandos para compilar, probar y comprobar bloques en JavaScript.
- [Detalle de etapa 1](docs/etapa-1.md): contrato RGB, límites y resultados.
- [Detalle de etapa 2](docs/etapa-2.md): preparación, índice y niveles de resolución.
- [Plan de desarrollo](docs/plan-desarrollo.md): ocho etapas y criterios de cierre.

El repositorio contiene código y documentación. `.gitignore` excluye imágenes,
resultados y clases compiladas. Cada avance funcional actualizará el estado del
plan y su evidencia de pruebas; los cambios se conservarán en commits sucesivos.
