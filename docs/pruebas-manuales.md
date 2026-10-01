# Guía de preparación y consulta

Guía complementaria al [README](../README.md).

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


