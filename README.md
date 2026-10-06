# PRIB — Visor de imágenes de alta resolución

Proyecto de Redes (CC8): un servidor asíncrono en Java envía al navegador
los bloques necesarios para explorar una imagen, sin descargarla completa.

**Etapas 1 a 7 implementadas:** preparación, almacenamiento, visor con zoom,
SHA-256, créditos, caché limitada, prioridades, generaciones y los cuatro modos
REUSE/REF/DELTA/FULL. DELTA es exacto y la recuperación selectiva reenvía solamente
el bloque afectado. Los cuatro modos y los fallos se demuestran con pruebas sintéticas;
la evaluación documentada de etapa 7 pasó con ocho imágenes, hasta 55 GB y cuatro clientes
simultáneos. [Pruebas y resultados](docs/pruebas.md#resultados-del-5-de-octubre-de-2026).
[Plan de desarrollo](docs/plan-desarrollo.md).

## 1. Requisitos

- **JDK 21**, con `java` y `javac` disponibles en la terminal.
- **PowerShell** y un navegador actualizado.
- Los ZIP de las imágenes que se quieran preparar dentro de `imagenes/`, sin descomprimir. Esa carpeta no viene al clonar: la [guía del equipo](docs/preparacion-equipo.md) incluye el enlace de descarga, los nombres y dónde colocarlos.

No se necesitan Maven ni Node.js para ejecutar el visor. Con las herramientas
y las imágenes disponibles, funciona sin internet.

Abre PowerShell en la raíz del proyecto, donde está este README.
Todos los comandos siguientes se ejecutan desde allí.

## 2. Preparar una imagen — solo la primera vez

Para tener las cuatro imágenes actuales (4, 17, 28 y 55 GB), seguir la
[guía de preparación compartida](docs/preparacion-equipo.md).

**Si ya tienes imágenes preparadas en `data/`, pasa al paso 3.**

También puedes iniciar el servidor sin imágenes y pulsar **Preparar imagen** en
la web: elige un ZIP de `imagenes/`, una **imagen local** o una **URL directa**
pública HTTP/HTTPS a la imagen, y un nombre nuevo. Verás el
avance y aparecerá en el selector al terminar, sin reiniciar.
PNG conserva el lector por filas para imágenes enormes. JPEG, GIF y BMP se
identifican por su firma, con límites de 64 MiB de archivo y 16 millones de
píxeles; GIF utiliza el primer fotograma.
Los comandos siguientes siguen disponibles como alternativa.

Para comenzar con la imagen pequeña del curso:

```powershell
./scripts/preparar.ps1 -Output 'data/imagen-782'
```

El comando busca el ZIP pequeño y prepara `000-001-800-5105.png`.
`data/imagen-782` es la **carpeta de destino**, no el nombre de la imagen.
Puedes elegir otro nombre; debe ser una carpeta nueva dentro de `data/`.

Para preparar otra imagen:

```powershell
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-28G-17G.zip' -Entry '017-110-000-24650032.png' -Output 'data/imagen-17gb'
```

| Parámetro | Qué representa |
| --- | --- |
| `-Zip` | Ruta del ZIP que contiene la imagen |
| `-Entry` | Nombre exacto del PNG dentro del ZIP |
| `-Output` | Carpeta nueva donde guardar la imagen preparada |

La preparación de imágenes grandes puede tardar varios minutos. No hace falta
repetirla cada vez que se abre el visor.

## 3. Iniciar el visor

```powershell
./scripts/servidor.ps1
```

Mantén esa terminal abierta y entra a **[http://localhost:8080](http://localhost:8080)**.
Para detener el servidor, presiona **Ctrl+C** en la terminal.

Si el puerto está ocupado, usa `./scripts/servidor.ps1 -Port 8081` y abre
`http://localhost:8081`. El servidor funciona únicamente en el equipo local.

Después de actualizar el código o preparar imágenes desde la terminal, reinicia
el servidor. Las preparadas desde la web se incorporan automáticamente.
Si el navegador sigue mostrando la versión anterior, usa **Ctrl+F5**.

## 4. Explorar la imagen

| Control | Uso |
| --- | --- |
| Imagen preparada | Seleccionar qué imagen explorar |
| Rueda o botones **+ / −** | Acercar y alejar; la rueda toma como referencia el cursor |
| Arrastrar | Desplazarse por la imagen |
| Nivel de detalle | El nivel 0 conserva la resolución original; los demás son versiones reducidas |
| X / Y e **Ir a la región** | Saltar a unas coordenadas en píxeles del nivel seleccionado |
| **Ver imagen completa** | Volver a la vista general |
| **Mostrar transferencia** | Revisar bloques, bytes, integridad y sesión |
| **Pantalla completa** | Ampliar el visor; salir con Escape |
| **Reconectar** | Iniciar una sesión nueva |
| **Preparar imagen** | Elegir ZIP, archivo local o URL (PNG/JPEG/GIF/BMP) y consultar el progreso |

El zoom puede superar el 100 % para ver los números más grandes: amplía los
píxeles originales sin añadir información. Solo se solicitan los bloques
que intersectan la región visible.

## 5. Comprobar que funciona

1. Selecciona una imagen, acerca a **400 %** y arrastra hacia otra zona.
2. Abre **Mostrar transferencia**. Al terminar deben aparecer **Vista completa**,
   los bloques recibidos y esperados coincidentes, y **SHA-256 correcto**.
3. Abre otra pestaña y explora una región distinta. Las sesiones deben ser
   diferentes y cada pestaña debe funcionar de forma independiente.
4. Prueba cambiar el tamaño de la ventana, entrar en pantalla completa
   y volver con **Ver imagen completa**.

Para ejecutar las pruebas automáticas:

```powershell
./scripts/test.ps1
```

Deben terminar sin errores y mostrar mensajes `PASS`. Comprueban lectura PNG,
almacenamiento y comunicación con dos clientes; no requieren los ZIP del curso.

Para comprobar créditos, reutilización, clientes independientes, DELTA y recuperación,
seguir la [guía de pruebas](docs/pruebas.md). También incluye las pruebas opcionales
de navegador, la evaluación completa y los resultados con imágenes de hasta 55 GB.

## Carpetas y documentación

| Carpeta | Contenido |
| --- | --- |
| `src/` | Código Java y pruebas |
| `web/` | Interfaz del visor |
| `scripts/` | Comandos de preparación, ejecución y pruebas |
| `imagenes/` | ZIP originales |
| `data/` | Imágenes preparadas; conservarlas para no repetir la preparación |
| `output/` | Resultados de pruebas y regiones exportadas |
| `build/` | Compilación y pruebas temporales |
| `docs/` | Plan, detalles técnicos y evidencia |

Los ZIP, imágenes preparadas y temporales no se suben a GitHub. Se conserva
únicamente el JSON de referencia de la evaluación documentada en `docs/mediciones/`.
Puedes limpiar los resultados de `output/` si no los necesitas; comprueba
antes que no hayas elegido esa carpeta para guardar algún almacén preparado.

| Documento | Para qué leerlo |
| --- | --- |
| [Preparación del equipo](docs/preparacion-equipo.md) | ZIP y preparación desde web o terminal de 4, 17, 28 y 55 GB |
| [Plan de desarrollo](docs/plan-desarrollo.md) | Consultar las ocho etapas y su estado |
| [Protocolo y arquitectura](docs/protocolo.md) | Entender mensajes, almacenamiento, algoritmos, créditos y caché |
| [Pruebas y resultados](docs/pruebas.md) | Probar manualmente, ejecutar verificaciones y consultar mediciones |

## Entregables locales de etapa 8

Se conservan el [documento PRIB anterior](docs/protocolo-prib.md),
la [guía de demostración](docs/demostracion.md), el [cierre local](docs/etapa-8.md)
y el [Word para Google Docs](docs/PRIB-Protocolo-y-Demostracion.docx).
Estos entregables se generaron antes de incorporar la preparación web y las
nuevas mediciones de 55 GB; el contrato actualizado está en docs/protocolo.md.

La demostración local pequeña usa JDK, Node, Chrome y Playwright ya instalados:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1
```

Si PowerShell bloquea el servidor por falta de firma digital:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\servidor.ps1
```
