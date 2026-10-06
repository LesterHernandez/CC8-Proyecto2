# Preparar las imágenes del equipo

El catálogo actual contiene **4, 17, 28 y 55 GB**. Los ZIP y almacenes son locales:
no vienen al clonar ni se descargan con git pull. Para comenzar basta una imagen.

## 1. Colocar los ZIP

Descargar desde el [Drive del ingeniero](https://drive.google.com/drive/folders/1eH_B2nnEAl9hpHm7vnIflyYpUyaIrxIB?usp=sharing)
y colocar, sin descomprimir, estos archivos en `imagenes/`, junto a `README.md`:

- `Imagenes-Pequeñas.zip`: incluye la de 4 GB.
- `Imagenes-28G-17G-Comprimidas.zip`: contiene las de 17 y 28 GB.
- `Imagen-55GB-comprimida.zip`: contiene la de 55 GB.

Si la carpeta no existe, crearla con `New-Item -ItemType Directory -Force imagenes`.
No se sube a GitHub. Los nombres deben coincidir con los comandos al usar la terminal.

## 2. Preparar desde la web

Con JDK 21 instalado, abrir PowerShell en la raíz y ejecutar:

```powershell
./scripts/servidor.ps1
```

1. Abrir http://localhost:8080 y pulsar **Preparar imagen**. Funciona también con `data/` vacío.
2. Pulsar **Actualizar lista** si se acaba de colocar un ZIP en `imagenes/`.
3. Seleccionar ZIP, PNG y escribir el nombre de destino de la tabla.
4. Pulsar **Preparar**. Se muestra el avance aproximadamente cada cinco segundos.
5. Al terminar, la imagen aparece en el selector de las pestañas conectadas sin reiniciar.

| ZIP | PNG dentro del ZIP | Nombre de destino |
| --- | --- | --- |
| Imagenes-Pequeñas.zip | 004-050-000-6650032.png | imagen-4gb |
| Imagenes-28G-17G-Comprimidas.zip | 017-110-000-24650032.png | imagen-17gb |
| Imagenes-28G-17G-Comprimidas.zip | 028-227-000-40650032.png | imagen-28gb |
| Imagen-55GB-comprimida.zip | 055-843-000-80450114.png | imagen-55gb |

**Omitir las imágenes ya preparadas.** El nombre crea una carpeta nueva en `data/`;
no es el nombre del PNG. Usar hasta 64 letras, números, guiones o guion bajo,
comenzando por letra o número. No se sobrescriben destinos existentes.

Solo hay una preparación activa por servidor. Cerrar el panel no
la cancela; volver a abrirlo recupera el estado. Mantener el servidor encendido:
detenerlo interrumpe la preparación. Las salidas incompletas no aparecen en el
catálogo; el panel muestra el error y permite reintentar con otro nombre.
Las otras imágenes siguen disponibles, aunque la preparación utiliza disco y CPU.

Esta opción usa ZIP presentes en el equipo del servidor; no sube archivos desde
otro equipo ni extrae el PNG completo. Admite PNG RGB8 sin entrelazado.
Comprueba espacio para el peor caso sin compresión: unos 37,61 GB para la de 28 GB
y 74,41 GB para la de 55 GB, más 256 MiB de reserva y aparte de los ZIP.

## Archivo local o URL directa

El panel **Preparar imagen** conserva **ZIP del servidor** y agrega dos fuentes:

- **Archivo local:** selecciona un archivo PNG, JPEG, GIF o BMP del equipo y un nombre nuevo. Se envía en fragmentos de 4096 bytes mientras se prepara. Mantén la pestaña conectada hasta terminar la subida; desconectarla antes interrumpe la tarea.
- **URL directa:** pega un enlace público HTTP/HTTPS cuyo contenido sea la imagen. Se admiten redirecciones públicas. Una página de Drive, una página HTML o un enlace que requiere iniciar sesión no sirven. Se bloquean destinos de red privada/local y URLs con credenciales.

Las tres opciones admiten PNG RGB de 8 bits sin entrelazado y JPEG/GIF/BMP.
El formato se detecta por la firma de los bytes, no por la extensión de la URL.
JPEG/GIF/BMP se decodifican a RGB8: máximo 64 MiB comprimidos, 16 millones de
píxeles y 32768 píxeles por lado. Usan un temporal comprimido acotado que se
elimina al decodificar; la imagen decodificada ocupa RAM. GIF utiliza el primer
fotograma; la transparencia se compone sobre blanco. No se admite WebP, SVG,
TIFF ni PNG RGBA/entrelazado en esta versión. Los hashes verifican los píxeles
RGB decodificados; JPEG ya es un formato con pérdida en el original.

PNG conserva el procesamiento por filas sin una copia temporal completa.
Todos generan bloques y niveles en `data/`, que siguen requiriendo espacio.
Usar una URL no elimina el requisito de disco de la imagen de 55 GB. No se
sobrescriben destinos existentes. Ejemplo de URL probado:
https://cdn.eso.org/images/screen/eso1242a.jpg (1280 × 964).

Cerrar el panel permite continuar. Una vez enviada la imagen local, cerrar la
pestaña también permite terminar; para ZIP y URL la tarea pertenece al servidor.
Si una preparación falla, conserva la salida incompleta y usa otro nombre al
reintentar. El catálogo se actualiza automáticamente para todas las pestañas.

## Alternativa: PowerShell

Ejecutar uno por uno desde la raíz; no preparar en paralelo desde web y terminal.
Omitir los destinos que ya estén preparados.

```powershell
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-Pequeñas.zip' -Entry '004-050-000-6650032.png' -Output 'data/imagen-4gb'
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-28G-17G-Comprimidas.zip' -Entry '017-110-000-24650032.png' -Output 'data/imagen-17gb'
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-28G-17G-Comprimidas.zip' -Entry '028-227-000-40650032.png' -Output 'data/imagen-28gb'
./scripts/preparar.ps1 -Zip 'imagenes/Imagen-55GB-comprimida.zip' -Entry '055-843-000-80450114.png' -Output 'data/imagen-55gb'
```

`-Zip` es el archivo descargado; `-Entry`, el PNG exacto; `-Output`, la carpeta
nueva. Los identificadores generados no tienen que coincidir entre equipos.
Si se prepara desde la terminal con el servidor abierto, reiniciarlo para detectar
ese almacén externo. Desde la web la actualización es automática.

## Después de actualizar el código

Detener el servidor con Ctrl+C cuando no esté preparando una imagen y ejecutar:

```powershell
git pull origin main
./scripts/servidor.ps1
```

Recargar con Ctrl+F5. No se preparan nuevamente las imágenes.
Ver [pruebas y resultados](pruebas.md) para las verificaciones y mediciones históricas.
