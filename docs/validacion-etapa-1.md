# Validación de la etapa 1

Fecha: 23 de septiembre de 2026.

## Entorno comprobado

- Windows, Java y javac 21.0.11.
- RAM física informada: 12557447168 bytes, aproximadamente 11.7 GiB.
- Espacio libre inicial: aproximadamente 114.7 GB decimales.
- ZIP original: 567839707 bytes; 10 PNG, 5734472906 bytes sumados sin ZIP.
- Las 10 cabeceras indican RGB8 sin entrelazado.
- Se extrajeron tres imágenes pequeñas y la de 4.06 GB a `data/originals`.
  ZIP, originales, clases y recortes están excluidos de Git.

## Pruebas automáticas

Comando: `./scripts/test.ps1`.

Resultado: **22 comprobaciones aprobadas**, ejecutadas con `-Xmx64m`:

- Dimensiones de cabecera y tamaño RGB calculado con aritmética de 64 bits.
- Píxeles exactos en origen, interior, borde inferior derecho, región de un píxel
  y una imagen pequeña completa, contrastados con un patrón conocido.
- Rechazo de origen negativo, dimensión cero, región fuera de bordes,
  coordenadas extremas y región superior al límite.
- Rechazo de escala de grises, firma inválida y datos truncados.
- Orden RGB canónico, hash sensible a dimensiones y vector SHA-256 independiente.

## Pruebas con las imágenes reales

| Origen | Región x,y,ancho,alto | Heap máximo | Tiempo de lectura | Resultado |
| --- | --- | --- | --- | --- |
| `000-001-800-5105.png`, 782 x 782 | 263,319,256,256 | 64 MiB | 0.265 s | PNG generado |
| `004-050-000-6650032.png`, 36743 x 36743 | 36487,36487,256,256 | 64 MiB | 62.794 s | PNG generado |

La segunda imagen ocupa **4056548979 bytes** y sus muestras RGB completas
ocuparían **4050144147 bytes**, muy por encima del heap autorizado. El proceso
terminó correctamente sin materializar esa imagen completa en el heap.

Estos tiempos corresponden a una ejecución local, no a un benchmark ni a una
promesa de rendimiento. La lectura del final del PNG requiere recorrer datos
anteriores. El límite de heap no equivale a toda la RAM del proceso JVM.

### Comprobación independiente de fidelidad

Se comparó el recorte pequeño con Pillow, usando el PNG original y las mismas
coordenadas: todos los bytes RGB coincidieron. El hash se calculó además con
`hashlib` y el prefijo PRIB documentado, y coincidió con Java:

```text
7295d65254926368d5f4514e76548560f6897ce38ac093bd09af0788fcb3e931
```

Pillow y Python se usaron únicamente para esta comprobación independiente;
no son dependencias de ejecución del proyecto.

Hash PRIB informado para el recorte grande:

```text
9ce24c80699d8e243fd185f1a6a6829b7448b8252b261f6bcde14448748a1e2d
```

El hash grande es un resultado de la ejecución, no una comparación con un hash
de referencia externo. No se ha validado exhaustivamente la integridad de todas
las imágenes del ZIP.

## Criterio de cierre

Se completó la base compilable, el contrato inicial de bloques/hash, las decisiones
de transporte y la lectura regional comprobada con memoria limitada. El código
y los scripts incluyen comentarios explicativos en español.

La etapa 2 queda pendiente: preprocesamiento secuencial eficiente, almacén de
bloques, metadatos y niveles de detalle. Red, frontend, créditos, caché, DELTA y
recuperación se implementarán en las etapas correspondientes.
