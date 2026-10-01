# PRIB — Protocolo de Reutilización Inteligente de Bloques

Proyecto de Ciencias de la Computación VIII para servir imágenes de ultra alta
resolución mediante un servidor asíncrono en Java. PRIB decidirá qué bloques
necesita cada cliente, cuáles puede reutilizar y cuándo transmitirlos según
sus créditos de recepción. La propuesta incluye REUSE, REF, DELTA y FULL.

**Estado actual: etapa 1 implementada y probada.** Tenemos la base de lectura y
verificación de bloques; todavía no hay servidor HTTP/WebSocket ni interfaz web.
Consulta el [plan de desarrollo de ocho etapas](docs/plan-desarrollo.md) para
conocer el alcance y los criterios de cierre de cada avance.

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
El preprocesador para imágenes grandes corresponde a la etapa 2.

## Requisitos y preparación

- JDK 21: `java -version` y `javac -version` deben mostrar la versión 21.
- PowerShell para ejecutar los scripts.
- ZIP de imágenes pequeñas del curso dentro de `imagenes/`, sin extraerlo.
- Node.js solo si se desea ejecutar la verificación adicional en JavaScript.

No se necesitan Maven ni paquetes externos. Una vez instaladas las herramientas
y descargadas las imágenes, las pruebas funcionan sin internet. El script
principal limita el heap de Java a 256 MiB; esto no es toda la RAM del proceso.

## Ejecutar las dos pruebas reales

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

Las pruebas Java generan una imagen sintética y comprueban los cinco filtros
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
- `src/test/java/prib/PngRowsTest.java`: casos sintéticos de verificación.
- `scripts/`: comandos para compilar, probar y comprobar bloques en JavaScript.
- [Detalle de etapa 1](docs/etapa-1.md): contrato RGB, límites y resultados.
- [Plan de desarrollo](docs/plan-desarrollo.md): ocho etapas y criterios de cierre.

El repositorio contiene código y documentación. `.gitignore` excluye imágenes,
resultados y clases compiladas. Cada avance funcional actualizará el estado del
plan y su evidencia de pruebas; los cambios se conservarán en commits sucesivos.
