# Etapa 2 — Preprocesamiento y almacenamiento por niveles

## Qué implementamos

El equipo dispone de un preprocesador que abre una imagen RGB8 sin entrelazado
desde su ZIP y genera un almacén consultable por bloques. Conserva la resolución
original y crea versiones reducidas en una sola lectura, sin extraer el PNG ni
cargar la imagen completa. Todo funciona con Java 21 y su biblioteca estándar.

Esta etapa aporta la infraestructura de PRIB. La compresión en disco no es DELTA
ni reemplaza el protocolo de control: sesiones, créditos y los cuatro modos de
transferencia se implementarán en las etapas posteriores.

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

No se extrae el original ni se crean temporales de imágenes completas. Los ZIP y
almacenes permanecen en el disco local y están excluidos de Git. Si una preparación
falla, los archivos parciales quedan marcados y no se sirven. No hay reanudación:
se debe repetir en una carpeta nueva; una salida existente nunca se sobrescribe.

## Cómo probar manualmente

Desde la raíz del repositorio:

```powershell
./scripts/preparar.ps1 -Output data/mi-imagen
./scripts/consultar.ps1 -Store data/mi-imagen -Level 0 -X 0 -Y 0 -Width 256 -Height 256 -Output output/mi-region.png
./scripts/consultar.ps1 -Store data/mi-imagen -Level 3 -Width 98 -Height 98 -Output output/mi-miniatura.png
Get-Content data/mi-imagen/result.txt
```

La primera orden usa la imagen de 782 × 782 del ZIP pequeño. Deben generarse cuatro
niveles y las dos consultas deben indicar `Región verificada y exportada`. Los PNG
exportados sí se pueden abrir en un visor normal. Las coordenadas pertenecen al
nivel seleccionado y la región debe estar dentro de él. Se limita cada consulta
a 4,194,304 píxeles para evitar reconstruir imágenes completas gigantes.

Para otra imagen o ZIP, proporcionar `-Entry` y opcionalmente `-Zip`:

```powershell
./scripts/preparar.ps1 -Entry '000-100-200-190032.png' -Output data/otra-imagen
./scripts/preparar.ps1 -Zip imagenes/Imagenes-28G-17G.zip -Entry '017-110-000-24650032.png' -Output data/otra-imagen-17gb
```

Las imágenes grandes pueden tardar varios minutos. El proceso muestra filas
procesadas y tiempo transcurrido cada cinco segundos aproximadamente.

## Verificación

`./scripts/test.ps1` conserva las pruebas de etapa 1 y agrega:

- Comparación píxel a píxel de todos los niveles contra una referencia independiente.
- Tamaños impares, dimensiones de un píxel, bordes y regiones que cruzan bloques.
- Almacenamiento tanto zlib como RGB directo.
- Rechazo de bloques alterados, regiones inválidas y carpetas ya existentes.
- Preparación interrumpida por PNG truncado: la imagen parcial no puede consultarse.

Además, las imágenes reales pequeñas se comparan completamente contra ImageIO.
En las grandes se verifican por hash muestras de las cuatro esquinas y del centro
de cada nivel, y se exportan regiones para inspección visual. Estas muestras no
equivalen a una comparación independiente de todos los píxeles de una imagen grande.

## Pendiente para el proyecto

La etapa 3 conectará este almacén con el servidor asíncrono y el navegador. Aún no
hay transferencia PRIB, sesiones ni control de créditos. La evaluación de varios
clientes y del sistema completo corresponde a las etapas posteriores.

## Mediciones de las ejecuciones de desarrollo

Todas estas preparaciones usan `-Xmx256m`. GB/MB de entrada corresponden al tamaño
del PNG, no al ZIP; la salida suma todos los niveles, índices y manifiesto.
Los tiempos y el heap son observaciones de una ejecución, no requisitos del equipo.

| Imagen | Niveles | Salida en bytes | Tiempo | Heap observado |
| --- | --- | --- | --- | --- |
| 782 × 782 | 4 | 502775 | 0.684 s | 8.60 MiB |
| 1890 × 1890 | 5 | 2730841 | 0.747 s | 22.34 MiB |
| 100 MB | 7 | 24841065 | 2.555 s | 111.51 MiB |
| 500 MB | 8 | 124309816 | 10.475 s | 119.16 MiB |
| 4 GB | 10 | 979003575 | 96.099 s | 132.22 MiB |
| 17 GB | 11 | 3987264674 | 358.526 s | 176.83 MiB |

La preparación completa de 17 GB terminó sin la marca `INCOMPLETE`. Después
pasó la lectura por SHA-256 de esquinas y centro de sus 11 niveles. También se
exportaron una región de 471 × 471 píxeles en la esquina inferior derecha del
nivel original y la miniatura de 74 × 74 del último nivel. Se inspeccionó la
región original: los números permanecen visibles y no hay huecos entre bloques.

El almacén de 17 GB tiene 24 archivos (22 de niveles, manifiesto y reporte),
frente a 348,100 bloques solo en el nivel original. La imagen de 28 GB aún no
se ha preparado ni validado; queda para ampliar las pruebas. Esto no demuestra
la evaluación completa del proyecto: todavía faltan servidor, cliente y PRIB.

Para repetir únicamente las muestras del almacén ya creado, después de compilar
las pruebas con `./scripts/test.ps1`:

```powershell
java -Xmx256m -cp build/classes prib.ImageStoreTest data/imagen-17gb
```

Debe aparecer `PASS muestras de esquinas y centro en 11 niveles`. No vuelve a
preparar la imagen ni necesita el ZIP; lee el almacén existente.