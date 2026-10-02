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
