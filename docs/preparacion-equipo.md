# Preparar las mismas seis imágenes del equipo

Ejecutar desde PowerShell en la raíz del repositorio, con JDK 21 instalado.
Los ZIP se descargan una sola vez desde el Drive compartido por el ingeniero;
no se incluyen en GitHub. 

## 1. Descargar y colocar los ZIP

**Al clonar el repositorio, la carpeta `imagenes/` no viene creada ni contiene
los ZIP**, porque está excluida de Git. Cada integrante debe crearla y colocar
allí los archivos descargados. Esto se hace una sola vez.

1. Abrir el [Drive de imágenes compartido por el ingeniero](https://drive.google.com/drive/folders/1eH_B2nnEAl9hpHm7vnIflyYpUyaIrxIB?usp=sharing).
2. Descargar específicamente estos dos archivos: el ZIP de **imágenes pequeñas**
   y el ZIP que contiene las imágenes de **17 y 28 GB**. El de 55 GB no se necesita
   para las seis imágenes que usamos actualmente como pruebas.
3. En la raíz del proyecto, junto a `README.md`, `scripts/` y `src/`, crear una
   carpeta llamada exactamente **`imagenes`**, sin tilde. Puede hacerse desde
   el explorador de archivos o desde PowerShell:

   ```powershell
   New-Item -ItemType Directory -Force imagenes
   ```

4. Copiar o mover los dos ZIP descargados desde Descargas hacia esa carpeta.
   **No descomprimirlos:** el programa lee las imágenes directamente desde los ZIP.
   Comprobar que sus nombres sean exactamente `Imagenes-28G-17G.zip` e
   `Imagenes-Pequeñas.zip`. Si la descarga añadió un sufijo como `(1)`, renombrar
   el archivo para que coincida con estos nombres.

La estructura debe quedar así:

```text
CC8-Proyecto2/
├── imagenes/
│   ├── Imagenes-28G-17G.zip
│   └── Imagenes-Pequeñas.zip
├── scripts/
├── src/
├── web/
└── README.md
```

Se puede comprobar desde PowerShell con:

```powershell
Get-ChildItem ./imagenes -Filter *.zip
```

Deben aparecer los dos nombres anteriores. Tener los ZIP en `imagenes/` todavía
no prepara las imágenes: el siguiente paso generará las seis carpetas de `data/`
que utiliza el visor. Aunque uno de los ZIP incluye una imagen de 28 GB, por ahora
solo prepararemos la de 17 GB de ese archivo.

## 2. Preparar cada imagen una sola vez

Ejecutar los comandos uno por uno y esperar a que cada uno termine sin errores.
**Si la carpeta de destino ya contiene un almacén preparado completo, omitir
ese comando.** No volver a preparar las imágenes después de cada git pull.
Una carpeta con el archivo INCOMPLETE no está lista: revisar el error anterior
antes de intentar preparar otra vez en una carpeta nueva.

```powershell
# Imagen de 782 × 782 píxeles
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-Pequeñas.zip' -Entry '000-001-800-5105.png' -Output 'data/imagen-782'

# Imagen de 1890 × 1890 píxeles
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-Pequeñas.zip' -Entry '000-010-700-24119.png' -Output 'data/imagen-1890'

# Imagen de aproximadamente 100 MB
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-Pequeñas.zip' -Entry '000-100-200-190032.png' -Output 'data/imagen-100mb'

# Imagen de aproximadamente 500 MB
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-Pequeñas.zip' -Entry '000-500-000-950032.png' -Output 'data/imagen-500mb'

# Imagen de aproximadamente 4 GB
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-Pequeñas.zip' -Entry '004-050-000-6650032.png' -Output 'data/imagen-4gb'

# Imagen de aproximadamente 17 GB
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-28G-17G.zip' -Entry '017-110-000-24650032.png' -Output 'data/imagen-17gb'
```

`-Zip` indica el archivo descargado; `-Entry`, la imagen exacta dentro de él;
`-Output`, la carpeta que se creará automáticamente. Conservar estos nombres
permite que todos tengan el mismo catálogo y utilicen las pruebas documentadas.
La preparación genera identificadores propios en cada equipo; no tienen que coincidir.

No se extrae el PNG completo. Aun así, debe haber espacio para los ZIP y los
almacenes: el preprocesador comprueba una reserva conservadora antes de comenzar.
Las imágenes grandes pueden tardar varios minutos. La de 28 GB no forma parte
de este conjunto de seis imágenes.

## 3. Abrir el visor

```powershell
./scripts/servidor.ps1
```

Visitar http://localhost:8080. El catálogo debe contener las seis carpetas
anteriores. Si el servidor estaba abierto durante la preparación, reiniciarlo.
Abrir dos pestañas para probar clientes independientes.

## Después de actualizar el código

Detener el servidor con Ctrl+C y ejecutar:

```powershell
git pull origin main
./scripts/servidor.ps1
```

Recargar el navegador con Ctrl+F5. Las imágenes de `data/` se conservan localmente;
Git no las descarga, actualiza ni vuelve a preparar.
