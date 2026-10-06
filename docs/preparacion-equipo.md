# Preparación del equipo y de las imágenes

Usar PowerShell desde la raíz del repositorio. En la PC servidora, instalar JDK 21
y comprobar `java -version` y `javac -version`. El [README](../README.md#requisitos-e-inicio)
contiene los comandos de inicio.

## Imágenes

Descargar los ZIP del [Drive del curso](https://drive.google.com/drive/folders/1eH_B2nnEAl9hpHm7vnIflyYpUyaIrxIB?usp=sharing)
y colocarlos **sin descomprimir** en `imagenes/`. Crear la carpeta si no existe:

```powershell
New-Item -ItemType Directory -Force imagenes
```

| ZIP | Imagen dentro del ZIP | Destino en data/ |
| --- | --- | --- |
| Imagenes-Pequeñas.zip | 004-050-000-6650032.png | imagen-4gb |
| Imagenes-28G-17G.zip | 017-110-000-24650032.png | imagen-17gb |
| Imagenes-28G-17G.zip | 028-227-000-40650032.png | imagen-28gb |
| Imagen-55G.zip | 055-843-000-80450114.png | imagen-55gb |

Basta una imagen para empezar. ZIP y almacenes no vienen por Git. Cada PC que sirva
imágenes debe prepararlas o recibir una copia de los almacenes completos. La PC
que solo usa el navegador como cliente remoto no necesita esos archivos ni Java.

## Preparación desde la web

1. Iniciar el servidor y abrir **Preparar imagen → ZIP del proyecto**.
2. Pulsar **Actualizar lista**, elegir ZIP y entrada, e indicar un nombre de destino.
3. Pulsar **Preparar**. Al terminar, aparece en el selector de todos los clientes.

El destino crea una carpeta en `data/`; no es el nombre del PNG. Usar hasta 64
letras, números, guiones o guiones bajos, comenzando por letra o número.
No se sobrescriben destinos. Solo se prepara una imagen a la vez; cerrar el panel
no cancela, pero detener el servidor interrumpe la tarea. Una salida `INCOMPLETE`
no se publica: reintentar en otro destino, sin reanudación parcial.

También se admite **archivo local** (preferible para archivos pequeños; mantener
la pestaña conectada durante la subida) o **URL pública directa** (requiere Internet;
no admite páginas de Drive, credenciales ni destinos privados). Los ZIP son los
almacenados en el servidor; el archivo local se envía desde la PC del navegador.

## Alternativa desde PowerShell

Ejecutar solo las imágenes que aún no estén preparadas:

```powershell
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-Pequeñas.zip' -Entry '004-050-000-6650032.png' -Output 'data/imagen-4gb'
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-28G-17G.zip' -Entry '017-110-000-24650032.png' -Output 'data/imagen-17gb'
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-28G-17G.zip' -Entry '028-227-000-40650032.png' -Output 'data/imagen-28gb'
./scripts/preparar.ps1 -Zip 'imagenes/Imagen-55G.zip' -Entry '055-843-000-80450114.png' -Output 'data/imagen-55gb'
```

`-Zip` es el archivo existente; `-Entry`, el nombre exacto dentro del ZIP;
`-Output`, una **carpeta nueva elegida por el usuario**, no el nombre de una imagen.
Si el servidor estaba abierto, reiniciarlo tras preparar por terminal.

Para una prueba pequeña de 782 × 782, usando el ZIP de imágenes pequeñas:

```powershell
./scripts/preparar.ps1 -Output 'data/prueba-manual'
```

## Formatos y espacio

- PNG: RGB de 8 bits sin entrelazado; procesamiento por filas. No admite RGBA ni grises.
- JPEG/GIF/BMP: hasta 64 MiB comprimidos, 16 millones de píxeles y 32768 por lado.
  GIF usa el primer fotograma; la transparencia se compone sobre blanco.
- No se admiten WebP, SVG ni TIFF. El tamaño RGB de la imagen no es el tamaño del ZIP.

La comprobación previa reserva todos los niveles como RGB sin comprimir e índices:
aproximadamente 37,61 GB para preparar 28 GB y 74,41 GB para 55 GB, más 256 MiB de
reserva y los ZIP. La salida comprimida puede ocupar menos.
Conservar `data/` evita repetir la preparación. No preparar desde terminal y web
a la vez sobre el mismo directorio.

## Uso entre dos PCs con Radmin VPN

1. Conectar ambas PCs a la misma red de Radmin y comprobar que aparecen conectadas.
2. Iniciar Radmin **antes** del servidor. En la PC con las imágenes, ejecutar:

   ```powershell
   ./scripts/servidor.ps1 -Red
   ```

3. En el servidor, abrir `http://localhost:8080`.
4. En la otra PC, abrir `http://IP-RADMIN-DEL-SERVIDOR:8080`, sustituyendo el texto
   por la IPv4 Radmin del servidor. La terminal muestra las IPs con su adaptador.

Sin `-Red` solo se permite acceso local. Si cambia la IP, reiniciar el servidor.
Para intercambiar roles, aplicar los mismos pasos en la otra PC con sus almacenes.
No se cambian IPs en el código. `-Port 8081` permite otro puerto: actualizar también
la URL y la regla de Firewall.

### Firewall de Windows

En la PC servidora, abrir `wf.msc` y crear una **regla de entrada → Puerto → TCP →
8080 → Permitir conexión**, llamada **PRIB Radmin**. Seleccionar el perfil de Radmin
(puede ser Público); si se desconoce, seleccionar los tres perfiles y restringir
la regla en **Propiedades → Ámbito**: IP local = Radmin del servidor e IP remota =
Radmin del compañero. Repetir en ambas PCs si ambas servirán y actualizar al cambiar IPs.

No desactivar el Firewall ni abrir puertos del router. `-Red` escucha en todas las
interfaces IPv4; revisar que otras reglas amplias para Java no anulen la restricción.
El visor no tiene autenticación: compartirlo con compañeros de confianza, quienes
también podrán usar la preparación web.

### Comprobación entre ambas PCs

Abrir regiones distintas simultáneamente; deben aparecer sesiones diferentes,
**Vista completa** y **SHA-256 correcto**. Reconectar una no debe interrumpir la otra.
Si no conecta, comprobar Radmin, IP, `-Red`, puerto y Firewall. Desde el cliente:

```powershell
Test-NetConnection -ComputerName 'IP-RADMIN-DEL-SERVIDOR' -Port 8080
```

Sustituir el texto por la IP real. Se espera `TcpTestSucceeded : True`.

## Evaluación sin Internet

Disponer previamente de JDK, navegador, ZIP o almacenes y, para pruebas automáticas,
las [herramientas de navegador](pruebas.md#herramientas-de-navegador).
El visor y sus dependencias se sirven localmente. Usar localhost y varias pestañas
para la demostración desconectada; no basarla en Radmin ni en descargas por URL.
