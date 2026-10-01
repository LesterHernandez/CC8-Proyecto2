# Etapa 3 — Servidor asíncrono y visor PRIB

## Alcance

Conectamos los almacenes de etapa 2 con un servidor Java 21 y un visor HTML/CSS/JS.
El servidor entrega todos los recursos por HTTP y mantiene una conexión WebSocket
PRIB por navegador. El cliente solicita una vista, recibe bloques FULL, verifica
SHA-256 y confirma su materialización mediante ACK. No requiere paquetes externos.

Todavía no implementamos créditos, REUSE, REF, DELTA, recuperación selectiva,
prioridad visual ni caché reutilizable. Los límites fijos de recursos de esta
etapa no son Credit-Based Flow Control.

## Iniciar el servidor

Si aún no hay imágenes preparadas, ejecutar primero:

```powershell
./scripts/preparar.ps1 -Output data/mi-imagen
```

Si ya hay almacenes completos en `data/`, no hay que prepararlos otra vez.
Desde PowerShell en la raíz del repositorio:

```powershell
./scripts/servidor.ps1
```

La terminal muestra `PRIB: http://localhost:8080` y la cantidad de imágenes.
Mantenerla abierta y visitar [http://localhost:8080](http://localhost:8080).
Ctrl+C detiene el servidor; mientras funciona la terminal no vuelve al prompt.

| Parámetro | Uso | Predeterminado |
| --- | --- | --- |
| `-Port` | Puerto local donde escuchar | `8080` |
| `-Data` | Carpeta padre que contiene las subcarpetas de imágenes preparadas | `data` |

Para otro puerto: `./scripts/servidor.ps1 -Port 8081`, luego abrir
`http://localhost:8081`. `-Data` no es el ZIP ni un PNG. El catálogo se carga al
arrancar: reiniciar el servidor después de agregar otro almacén. Se omiten las
carpetas incompletas. Todos los scripts resuelven rutas desde el proyecto.

## Prueba manual con dos clientes

1. Abrir dos pestañas en la dirección del servidor. Deben mostrar sesiones distintas.
2. Seleccionar una imagen pequeña en una y otra imagen preparada en la segunda,
   por ejemplo `imagen-17gb` si existe localmente.
3. Elegir nivel 0 para máximo detalle. Arrastrar o introducir X/Y y pulsar
   **Ir a la región**. Las coordenadas son píxeles del nivel seleccionado.
4. Esperar **Vista completa**, contadores de bloques coincidentes y
   **SHA-256 correcto**. Las vistas de ambas pestañas son independientes.
5. Probar rueda o selector de nivel. **Ver imagen completa** solicita una versión
   reducida que quepa en pantalla, no descarga el original gigante completo.
6. Cerrar una pestaña: la otra debe seguir funcionando. **Reconectar** crea otra sesión.

El panel muestra bytes RGB de la vista actual, no bytes totales WebSocket/TCP.
Volver a una vista vuelve a enviar FULL. El visor conserva su Canvas actual;
no guarda una caché de bloques para reutilización todavía.

## Organización del código

| Archivo | Responsabilidad |
| --- | --- |
| `PribServer.java` | HTTP, conexiones NIO, Selector, colas y trabajadores de disco |
| `PribSession.java` | Estado por cliente, comandos, selección de bloques, FULL y ACK |
| `WebSocketFrames.java` | Máscara, longitudes, texto fragmentado, ping/pong y cierre |
| `Json.java` | Controles JSON planos y serialización del catálogo y respuestas |
| `web/app.js` | Vistas, SHA-256, Canvas, ACK y conexión del navegador |
| `web/index.html`, `web/style.css`, `web/icon.svg` | Interfaz y recursos locales |
| `PribServerTest.java` | Pruebas HTTP/WebSocket con sesiones independientes |

El Selector atiende conexiones no bloqueantes. Cuatro trabajadores realizan las
lecturas de ImageStore, descompresión y hash. Devuelven sus resultados al Selector;
solo ese hilo modifica las sesiones y colas. Los metadatos y recursos web pequeños
se cargan antes de aceptar conexiones. El código está comentado en español.

## Mensajes PRIB versión 1

Los controles son JSON UTF-8. Los entrantes admiten campos planos de texto o
enteros; el catálogo saliente incluye una lista de imágenes. Todos llevan
`version`. Después de HELLO, el cliente debe incluir `sessionId`.

| Mensaje | Dirección | Contenido y efecto |
| --- | --- | --- |
| HELLO | C → S | Negociar `version: 1`; una vez por conexión |
| IMAGE_INFO | S → C | `sessionId`, `blockSize` y catálogo `images` con identidad, nombre, dimensiones y niveles |
| VIEW | C → S | `imageId`, `viewId` creciente, `level`, `x`, `y`, `width`, `height` |
| VIEW_ACCEPTED | S → C | Vista aceptada y cantidad esperada de bloques |
| BLOCK_FULL | S → C | Bloque RGB completo, identificado y verificable |
| ACK | C → S | `viewId`, `transferId` y `hash` del bloque materializado |
| VIEW_DONE | S → C | Fin de envío de la vista y cantidad de bloques |
| ERROR | S → C | Motivo del error; esta etapa cierra la conexión |
| CLOSE | C → S | Cierre ordenado de sesión |

VIEW_DONE no implica que ya llegaron todos los ACK. El navegador declara Vista
completa después de verificar todos los bloques en orden. VIEW_ACCEPTED,
VIEW_DONE y ERROR hacen explícito el avance del recorrido inicial.

Ejemplo de VIEW; sustituir los identificadores por los recibidos en IMAGE_INFO:

```json
{"version":1,"type":"VIEW","sessionId":"sesion-recibida","imageId":"imagen-del-catalogo","viewId":1,"level":0,"x":0,"y":0,"width":256,"height":256}
```

Cada mensaje binario BLOCK_FULL contiene:

1. Longitud de cabecera: uint32 big-endian.
2. Cabecera JSON UTF-8: `version`, `type`, `sessionId`, `viewId`, `transferId`,
   `imageId`, `level`, `blockId`, `x`, `y`, `width`, `height`, `format`, `codec`,
   `payloadLength` y `expectedHash`.
3. Píxeles RGB canónicos: `format=RGB8`, `codec=RAW`, hash SHA-256 hexadecimal.

La compresión de los packs es independiente: ImageStore reconstruye RGB antes
de enviarlo. Los bloques son de 128 × 128 o bordes parciales. Si intersectan
parcialmente la vista, incluyen también sus píxeles fuera del rectángulo exacto.

## Vistas, límites y alcance local

- VIEW sustituye la lista pendiente anterior. Un trabajo de disco ya iniciado
  puede terminar, pero se descarta su resultado si la vista cambió.
- Los bloques ya enviados pueden llegar tarde; el navegador revisa viewId y no
  los dibuja en la nueva vista. Esto aún no es CANCEL ni gestión de dependencias.
- Un ACK de una vista sustituida se ignora. Un ACK actual desconocido o con hash
  distinto genera ERROR y cierre.
- Máximo 32 conexiones, 100 imágenes de catálogo y 3840 × 2160 píxeles por VIEW.
  El Canvas se adapta al espacio disponible hasta ese límite, sin estirar los píxeles.
- Cabeceras HTTP y controles entrantes de hasta 8 KiB. Cola de salida de hasta
  1 MiB y 128 elementos. Como máximo un bloque en preparación por sesión.
- Pool de cuatro trabajadores y 32 tareas en espera. Hasta 1024 transferencias
  sin ACK por sesión/vista; no hay concesión de créditos todavía.
- Cola de verificación del navegador limitada a 32 MiB, procesada en orden.
  Un exceso o fallo de integridad cierra la conexión.
- Ping WebSocket cada 20 segundos, cierre tras 60 segundos sin respuesta.
  El navegador responde pong automáticamente. HTTP incompleto vence en 10 segundos.

`-Xmx256m` limita el heap, no toda la RAM de Java. El servidor escucha únicamente
en `127.0.0.1`, para pruebas locales con varias pestañas. No se configura TLS ni
acceso desde otros equipos en esta etapa. El navegador usa SHA-256 en el contexto
local de localhost. Este servidor no es un servidor web de propósito general.

## Evidencia de pruebas

`./scripts/test.ps1` conserva las pruebas anteriores y agrega un servidor temporal
en un puerto libre, imágenes sintéticas y dos clientes Java. No ocupa el puerto
8080 ni requiere los ZIP del curso para estas pruebas.

Se comprobó:

- HTTP de recursos locales y rechazo de rutas directas a almacenes.
- Tramas parciales, texto fragmentado, máscara, ping y claves JSON duplicadas.
- Dos sesiones con vistas independientes y comparación FULL exacta con ImageStore.
- Cambios de generación, bordes, ACK y desconexión de un cliente.
- Rechazo de sesión ajena y versión no soportada.
- Dos pestañas de Chrome: imagen pequeña y almacén de 17 GB, arrastre, cambios
  rápidos de nivel, esquina final y reconexión. Sin errores JavaScript ni
  solicitudes externas observadas. Una región de 17 GB recibió 40 bloques
  verificados (1,966,080 bytes RGB), no la imagen completa.

El recorrido del navegador se verificó como QA de desarrollo. La prueba manual
anterior permite repetirlo sin herramientas de automatización. Las pruebas de
carga y políticas del protocolo corresponden a las siguientes etapas.

## Referencias

- [WebSocket RFC 6455](https://www.rfc-editor.org/rfc/rfc6455.html)
- [Canales seleccionables Java 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SelectableChannel.html)

### Visor adaptable

La imagen aprovecha el ancho y alto disponibles. **Mostrar transferencia** abre
el panel de métricas; **Ocultar transferencia** devuelve ese espacio al visor.
**Pantalla completa** amplía la aplicación; se sale con el mismo botón o Escape.
Al redimensionar la ventana o cambiar el panel se solicita la nueva región visible.
Al 100 %, el nivel 0 conserva un píxel original por píxel CSS. Por encima de
100 %, se amplían sus píxeles sin suavizado. El Canvas mantiene un área máxima
de 3840 × 2160; no se descarga el original completo.

Para comprobarlo, selecciona nivel 0, cambia el tamaño de la ventana, alterna el
panel y entra/sal de pantalla completa. Cada vista debe terminar con bloques
verificados y sin mezclar regiones. En ventanas estrechas, el panel se superpone.

Se verificó también el visor adaptable en Chrome: panel abierto/cerrado, entrada
y salida de pantalla completa y ventanas de 700 × 800, 1920 × 1080 y 3840 × 2160.
La vista de prueba en 4K completó 496 bloques con SHA-256 correcto, sin errores JavaScript.

### Acercar y explorar detalles

- Rueda hacia arriba o **+**: acercar. Primero se recorre la pirámide hasta el
  nivel 0; después se amplía el original a 200 %, 400 %, 800 % y más.
- Rueda hacia abajo o **−**: alejar, recorriendo el camino inverso.
- La rueda conserva el punto bajo el cursor, salvo el ajuste necesario en los
  bordes. Los botones usan el centro del visor.
- Arrastrar desplaza la región proporcionalmente al aumento. X/Y siguen siendo
  coordenadas en píxeles del nivel elegido, no píxeles de pantalla.
- **Ver imagen completa** restablece la vista general. Seleccionar un nivel
  manualmente restablece su escala de visualización a 1:1.

El porcentaje se refiere al tamaño original: 100 % es nivel 0 sin ampliación.
El aumento digital no añade información; permite inspeccionar los píxeles sin
suavizarlos. Tiene un límite técnico de 65,536 veces (6,553,600 %), muy superior
al necesario para que un solo píxel ocupe toda la pantalla.

La región solicitada se calcula dividiendo el tamaño del visor por el aumento,
redondeando sus límites hacia fuera. Solo se reciben los bloques de 128 × 128
que la intersectan; un bloque puede incluir píxeles fuera del área visible.
Cada respuesta conserva la escala y posición de su vista para evitar mezclar
bloques mientras se navega. La verificación SHA-256 y los ACK siguen activos.

**Prueba manual:** en nivel 0, anotar los bytes recibidos; acercar a 400 %,
arrastrar y revisar los números. La región solicitada debe ser menor y seguir
terminando en Vista completa con SHA-256 correcto. Probar después los bordes,
cambios rápidos y Ver imagen completa. El número de bloques cambia por saltos,
según sus límites; no tiene por qué disminuir en cada movimiento.

Verificación adicional en Chrome con el almacén de 17 GB: zoom 2x, 4x y máximo,
anclaje al cursor (tolerancia inferior a un píxel por redondeo del puntero),
arrastre proporcional, solicitudes reducidas, bordes e integridad correctos.
