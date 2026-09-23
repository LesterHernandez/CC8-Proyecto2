# Decisiones de la etapa 1

## Objetivo

Establecer una base reproducible y comprobar lectura regional con memoria
acotada antes de crear el preprocesador y el servidor. Las funciones descritas
como futuras son decisiones de diseño, no funcionalidades implementadas.

## Base técnica

- Java 21, estructura `src/main/java` y `src/test/java`.
- Compilación directa con `javac --release 21`, sin dependencias externas.
- Un solo servidor Java y frontend HTML/CSS/JavaScript local en etapas posteriores.
- Scripts PowerShell para el entorno actual; código Java independiente del sistema.
- Comentarios en español para responsabilidades, decisiones y lógica importante.
- Las imágenes y los resultados regenerables se mantienen fuera de Git.

No añadimos librerías de red o herramientas de construcción hasta necesitarlas.
Las dependencias futuras deberán poder distribuirse y ejecutarse sin internet.

## Transporte elegido para el diseño

HTTP entregará HTML/CSS/JS; un canal WebSocket sobre TCP llevará los mensajes PRIB
entre el navegador y el mismo servidor Java. El protocolo PRIB decidirá bloques,
créditos, prioridad, cancelación, caché y recuperación. WebSocket solo transportará
sus mensajes. Aún no se implementa la red en esta etapa.

La propuesta aprobada deja abierta la interfaz concreta de transporte. WebSocket
es nuestra elección de implementación; no se atribuye al ingeniero una aprobación
específica de esta elección. TCP ya aporta entrega ordenada de bytes: ACK/RECOVER
de PRIB describirán estado de bloques y errores de reconstrucción, no segmentos TCP.

Formato previsto para la etapa 3: controles JSON legibles y datos de bloques en
mensajes binarios, sin convertir píxeles a Base64. La cabecera exacta, longitudes,
validaciones y ejemplos se fijarán antes de implementar el canal. No duplicaremos
una implementación completa de WebSocket si una dependencia pequeña y local basta.

## Bloques y fidelidad

- Tamaño inicial de mosaico: 256 x 256 píxeles; bordes con dimensiones reales.
- Límite del lector de diagnóstico: 512 x 512 por región.
- Nivel 0 representará resolución original; cada nivel posterior reducirá la
  resolución por un factor de dos. Los niveles se crearán en la etapa 2.
- Contenido canónico: muestras RGB8, filas de arriba abajo, píxeles de izquierda
  a derecha, tres bytes R/G/B por píxel y sin relleno entre filas.
- No se aplican conversiones de color o pérdidas a las muestras originales.
- FULL y la reconstrucción DELTA deberán producir exactamente ese contenido.
- El identificador espacial futuro incluirá imagen, nivel, columna y fila;
  el hash de contenido es independiente de esa posición, permitiendo REF.

Hash implementado: SHA-256 de la concatenación siguiente:

```text
ASCII PRIB       4 bytes
versión         1 byte, valor 1
canales         1 byte, valor 3
ancho           4 bytes, entero positivo big-endian
alto            4 bytes, entero positivo big-endian
píxeles         ancho * alto * 3 bytes, orden RGB
```

La cadena mostrada es hexadecimal minúscula. Este hash identifica contenido y
geometría; no es el hash de los bytes del archivo PNG comprimido.

## Recursos y créditos futuros

Un bloque completo de 256 x 256 tiene 196608 bytes RGB sin comprimir. Los límites
de crédito se elegirán considerando cabeceras y tamaño máximo transmitible para
que un bloque nunca quede esperando una concesión imposible.

Se contabilizarán los bytes PRIB de datos definidos por la futura especificación;
los mensajes esenciales de control estarán exentos de ese crédito y tendrán sus
propios límites. La capacidad de recepción/procesamiento y la caché residente son
presupuestos diferentes. ACK no devuelve crédito automáticamente.

La prueba actual limita el heap Java a 64 MiB. Esto no es un presupuesto definitivo
del servidor ni incluye toda la memoria nativa del proceso. El lector también
limita el ancho del PNG a 1000000 píxeles para acotar buffers dependientes de filas.

## Alcance del lector

Se utiliza `ImageReader` del JDK con `ImageReadParam.setSourceRegion` y
`FileImageInputStream`. El destino es solo la región; el archivo original se lee
desde disco. No se usa `ImageIO.read` sobre la imagen grande completa.

Esta implementación es una prueba de viabilidad. No es un decodificador universal
ni una validación exhaustiva de PNG. Rechaza profundidad, tipo de color y
entrelazado fuera del formato de las muestras. La etapa 2 deberá resolver el
recorrido secuencial eficiente antes de procesar todos los bloques de imágenes grandes.

## Fuentes técnicas

- [WebSocket, RFC 6455](https://www.rfc-editor.org/rfc/rfc6455.html).
- [Java 21 ImageReadParam](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/imageio/ImageReadParam.html).
- [Java 21 FileImageInputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/imageio/stream/FileImageInputStream.html).
- [Especificación PNG](https://www.w3.org/TR/png-3/).
- Base funcional: propuesta PRIB aprobada aportada por el usuario, PDF del curso
  y posteriores aclaraciones del ingeniero. Evaluación: frontend 20 %, backend
  20 % y documento del protocolo 60 %.
