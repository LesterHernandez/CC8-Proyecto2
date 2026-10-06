# Preparar las imágenes del equipo

El catálogo actual contiene **4, 17, 28 y 55 GB**. Los ZIP y almacenes son locales:
no vienen al clonar ni se descargan con git pull. Para comenzar basta una imagen.

## 1. Colocar los ZIP

Descargar desde el [Drive del ingeniero](https://drive.google.com/drive/folders/1eH_B2nnEAl9hpHm7vnIflyYpUyaIrxIB?usp=sharing)
y colocar, sin descomprimir, estos archivos en `imagenes/`, junto a `README.md`:

- `Imagenes-Pequeñas.zip`: incluye la de 4 GB.
- `Imagenes-28G-17G.zip`: contiene las de 17 y 28 GB.
- `Imagen-55G.zip`: contiene la de 55 GB.

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
| Imagenes-28G-17G.zip | 017-110-000-24650032.png | imagen-17gb |
| Imagenes-28G-17G.zip | 028-227-000-40650032.png | imagen-28gb |
| Imagen-55G.zip | 055-843-000-80450114.png | imagen-55gb |

**Omitir las imágenes ya preparadas.** El nombre crea una carpeta nueva en `data/`;
no es el nombre del PNG. Usar hasta 64 letras, números, guiones o guion bajo,
comenzando por letra o número. No se sobrescriben destinos existentes.

Solo hay una preparación activa por servidor. Cerrar el panel o la pestaña no
la cancela; volver a abrirlo recupera el estado. Mantener el servidor encendido:
detenerlo interrumpe la preparación. Las salidas incompletas no aparecen en el
catálogo; el panel muestra el error y permite reintentar con otro nombre.
Las otras imágenes siguen disponibles, aunque la preparación utiliza disco y CPU.

Esta opción usa ZIP presentes en el equipo del servidor; no sube archivos desde
otro equipo ni extrae el PNG completo. Admite PNG RGB8 sin entrelazado.
Comprueba espacio para el peor caso sin compresión: unos 37,61 GB para la de 28 GB
y 74,41 GB para la de 55 GB, más 256 MiB de reserva y aparte de los ZIP.

## Alternativa: PowerShell

Ejecutar uno por uno desde la raíz; no preparar en paralelo desde web y terminal.
Omitir los destinos que ya estén preparados.

```powershell
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-Pequeñas.zip' -Entry '004-050-000-6650032.png' -Output 'data/imagen-4gb'
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-28G-17G.zip' -Entry '017-110-000-24650032.png' -Output 'data/imagen-17gb'
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-28G-17G.zip' -Entry '028-227-000-40650032.png' -Output 'data/imagen-28gb'
./scripts/preparar.ps1 -Zip 'imagenes/Imagen-55G.zip' -Entry '055-843-000-80450114.png' -Output 'data/imagen-55gb'
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
