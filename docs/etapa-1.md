# Etapa 1 — Lectura y verificación de bloques

## Objetivo del equipo

Comprobar que podemos leer una imagen desde un ZIP, dividirla en bloques y
recuperar sus píxeles sin cambios. Esta etapa prepara la base de PRIB; todavía
no implementa el servidor, el navegador ni la transferencia por red.

## Implementación

El código principal tiene dos clases Java:

- **PngRows** lee PNG RGB de 8 bits sin entrelazado. Mantiene dos filas,
  revierte los cinco filtros PNG y comprueba los CRC de los chunks.
- **Probe** reúne hasta 128 filas, recorta bloques, los guarda y compara
  todos sus píxeles con ImageIO, un decodificador independiente.

ImageIO carga la imagen pequeña completa únicamente para verificar resultados.
Por eso la prueba admite como máximo diez millones de píxeles. El lector por
filas no necesita cargar toda la imagen. La ejecución limita el heap a 256 MiB;
este límite no representa toda la memoria del proceso Java.

## Formato de los bloques

- Tamaño nominal: **128 × 128**; bordes con dimensiones reales, sin relleno.
- Origen superior izquierdo, filas de izquierda a derecha y de arriba abajo.
- Cada píxel ocupa tres bytes sin signo: **R, G, B**.
- Longitud: `ancho × alto × 3`; máximo de 49,152 bytes por bloque.
- SHA-256 sobre los bytes RGB, expresado en hexadecimal minúsculo.
- Identificador de prueba: `columna_fila`. El manifiesto `blocks.csv` registra
  posición, dimensiones, longitud y hash; `result.txt` resume la ejecución.
- JavaScript verifica longitud y hash, y agrega alfa 255 para representar RGBA.
  El alfa no forma parte del hash. FULL y DELTA deberán reconstruir el mismo RGB.

Las pruebas sintéticas están en `src/test/java/prib/PngRowsTest.java` y se ejecutan con `./scripts/test.ps1`, usando el mismo JDK 21. Para el contexto de las siguientes etapas, consulta el [plan de desarrollo](plan-desarrollo.md).

## Verificaciones realizadas

| Caso | Resultado esperado y comprobado |
| --- | --- |
| Imagen de 782 × 782 | 49 bloques idénticos a ImageIO |
| Imagen de 1890 × 1890 | 225 bloques idénticos a ImageIO |
| PNG sintético de 259 × 131 | Cinco filtros, múltiples IDAT y bordes parciales |
| CRC incorrecto y PNG truncado | Rechazo con el error correspondiente |
| Lectura desde JavaScript | Longitudes, hashes y conversión RGB a RGBA correctos |

Los tiempos y el heap observado se registran en cada ejecución, porque varían
según el entorno. El muestreo de heap incluye ImageIO y no es un pico exacto.
Los resultados de imágenes pequeñas no garantizan rendimiento en las grandes.

## Límites y decisiones para continuar

Las doce cabeceras inspeccionadas son RGB8 sin entrelazado. Las imágenes de
17 y 28 GB miden 75,471 × 75,471 y 96,922 × 96,922 respectivamente; aún no se
han procesado completas. No se aplican transformaciones de color a las muestras.
El lector no pretende validar todos los formatos o extensiones de PNG.

La franja y las dos filas requieren aproximadamente `ancho × 3 × 130` bytes,
más bloques temporales, buffers y verificación. Los `.rgb` individuales son un
formato de prueba: en etapa 2 evaluaremos compresión, agrupación e índices antes
de generar cientos de miles de archivos. También crearemos niveles de resolución.

Para la futura comunicación mantenemos Java 21 y recursos web locales:
HTTP inicial y **PRIB sobre WebSocket/TCP**, con control JSON y bloques binarios.
Los mensajes identificarán sesión, vista, transferencia y bloque. Los créditos
se medirán en bytes PRIB; ACK confirmará materialización y no devolverá créditos
automáticamente. El control esencial no dependerá de créditos de datos.
Estos mecanismos, sus límites y formatos definitivos se implementarán y
validarán en sus etapas correspondientes; no forman parte de esta prueba.

## Referencias

- [Formato PNG y filtros](https://www.w3.org/TR/png-3/)
- [WebSocket](https://www.rfc-editor.org/rfc/rfc6455.html)
- [TCP](https://www.rfc-editor.org/rfc/rfc9293.html)
- [Referencia conceptual de créditos](https://www.rfc-editor.org/rfc/rfc9893.html)
