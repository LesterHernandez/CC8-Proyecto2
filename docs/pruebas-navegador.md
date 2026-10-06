# Pruebas opcionales de navegador

Estas pruebas están en `scripts/browser/` para que el equipo pueda repetirlas.
No son necesarias para ejecutar el visor ni para `./scripts/test.ps1`.

## Preparación

Se necesita Node.js, Google Chrome instalado y Playwright. La suite fue
verificada con Playwright 1.62.1. Desde la raíz del repositorio, instalar la
herramienta una vez (requiere internet):

```powershell
npm install --prefix build/browser-tools --no-save --package-lock=false playwright@1.62.1
```

La dependencia queda en `build/`, fuera de Git. Si se limpia esa carpeta,
hay que instalarla otra vez; los scripts de prueba sí permanecen en el repositorio.
No se descarga otro navegador: se utiliza Chrome instalado.

Estas pruebas usan la imagen del curso de **17 GB, de 75471 × 75471 píxeles**,
preparada en `data/imagen-17gb`, además de una imagen pequeña preparada.
Las coordenadas de prueba corresponden a esa imagen, no a un PNG cualquiera.
Consultar el [README](../README.md) si aún no están preparadas.

## Ejecución

En una terminal, dejar el servidor activo:

```powershell
./scripts/servidor.ps1
```

En otra terminal, desde la raíz del repositorio:

```powershell
$env:PRIB_PLAYWRIGHT = (Resolve-Path 'build/browser-tools/node_modules/playwright').Path
node ./scripts/browser/run.cjs
```

Deben aparecer mensajes `PASS` y finalizar sin errores. Las pruebas abren
navegadores sin ventana, los cierran al terminar y dejan capturas en
`build/browser-check/`. No modifican los almacenes ni detienen el servidor.

| Script | Qué comprueba |
| --- | --- |
| `sessions.cjs` | Dos sesiones, navegación rápida, bordes y reconexión |
| `zoom.cjs` | Acercamiento, región solicitada, anclaje al cursor y arrastre proporcional |
| `responsive.cjs` | Panel, pantalla completa, ventanas estrechas y tamaño 4K |
| `credits.cjs` | Pausa, reanudación, concesiones repetidas y sesión nueva sin deuda |
| `reuse.cjs` | Repetición con REUSE, bytes evitados, generaciones, caché y reconexión |

También se puede ejecutar uno: `node ./scripts/browser/credits.cjs`.
La suite se detiene en el primer fallo y devuelve un código de salida distinto de cero.

## Configuración opcional

```powershell
$env:PRIB_URL = 'http://localhost:8081'
$env:PRIB_IMAGE = 'nombre-de-la-carpeta-de-la-imagen-17gb'
```

`PRIB_URL` debe coincidir con el servidor iniciado. `PRIB_IMAGE` cambia el nombre
mostrado en el catálogo, no la imagen ni las dimensiones requeridas por las pruebas.
Si Chrome está en una ubicación distinta, `PRIB_BROWSER_PATH` permite indicar
la ruta completa a un ejecutable compatible. `PRIB_PLAYWRIGHT` puede apuntar a
otra instalación existente de Playwright. Ningún script contiene rutas personales.

## Reutilización y política de caché de etapa 5

`reuse.cjs` usa la primera imagen pequeña del catálogo. Repite su vista y compara
FULL inicial con REUSE, verifica el límite de RGB y la liberación de bases tras
cancelaciones rápidas. No necesita la imagen de 17 GB si se ejecuta por separado.
La suite completa mantiene esa imagen como requisito para los otros scripts.

Para demostrar REF en el navegador, preparar dos almacenes pequeños de la misma
imagen en una carpeta de datos de prueba, iniciar el servidor sobre ella y usar
`PRIB_REF_IMAGE` con el nombre de la segunda carpeta. La prueba es opcional y
requiere contenido y geometría idénticos; un parecido visual no sirve como REF.
La integración Java crea este caso automáticamente sin los ZIP del curso.

La política JS también puede probarse sin Chrome ni Playwright:

```powershell
node ./scripts/test-cache.cjs
```

El contrato de reutilización base está en [etapa-5.md](etapa-5.md);
el contrato vigente incorpora [etapa-6.md](etapa-6.md).

## DELTA y recuperación de etapa 6

`delta.cjs` se ejecuta por separado contra la fixture 512 × 128 generada por
`./scripts/test.ps1`, usando `build/delta-expected.json` de esa misma ejecución.
La suite imprime su carpeta `build/delta-test-.../data`; iniciar el servidor con
`-Data` apuntando allí y configurar `PRIB_URL` con su puerto antes de ejecutar:

```powershell
node ./scripts/browser/delta.cjs
```

Comprueba los cuatro modos, hashes y recuperaciones reales por base ausente,
hash incorrecto, DELTA truncado y caché desajustada, sin cerrar la sesión.
Sus fallos inyectados se limitan al navegador de prueba. No integra `run.cjs`
porque requiere un catálogo sintético diferente al de la imagen de 17 GB.
Detalles y comandos completos en [etapa-6.md](etapa-6.md).

También puede verificarse Java/JS sin navegador, después de ejecutar la suite Java:

```powershell
node ./scripts/test-delta.cjs
```

## Evaluación de etapa 7

`./scripts/evaluar.ps1` inicia un servidor aislado y ejecuta
`scripts/browser/evaluation.cjs`: regiones originales en todo el catálogo,
repeticiones, 40 regiones de la imagen mayor, cuatro clientes simultáneos,
pausa y reanudación de créditos, 32 generaciones rápidas, reconexión con deuda
y recuperación de un bloque alterado en el receptor. Produce JSON/CSV y captura
en `build/`. Requisitos, metodología y resultados en [etapa-7.md](etapa-7.md).

## Demostración final de etapa 8

`./scripts/demo.ps1` crea una fixture pequeña y ejecuta pruebas Java y Chrome,
sin imágenes grandes. Guion, dependencias offline, comandos y resultados
en [demostracion.md](demostracion.md) y [etapa-8.md](etapa-8.md).
