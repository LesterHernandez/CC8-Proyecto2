# Pruebas y verificación

Ejecutar desde PowerShell, en la raíz del repositorio. La prueba manual está en
[demostración](demostracion.md); el inicio y la preparación, en el [README](../README.md).

## Qué ejecutar

| Comando | Requisitos | Verificación |
| --- | --- | --- |
| `./scripts/test.ps1` | JDK 21 y PowerShell | PNG, almacenes, modos, créditos, caché, recuperación, preparación, acceso local/red y logs |
| `./scripts/demo.ps1` | Los anteriores, Node.js, Chrome y Playwright | Muestra sintética, cuatro modos, fallos recuperables y dos clientes |
| `./scripts/demo.ps1 -Red` | Los anteriores e interfaz IPv4 activa | Añade localhost/IP simultáneos y SHA-256 en HTTP por IP |
| `./scripts/evaluar.ps1` | Herramientas de navegador y almacenes grandes en data/ | Regiones reales, cuatro clientes, recursos, cancelación y recuperación |

Se espera **PASS** y salida sin errores. Los servidores de prueba usan puertos libres
y se cierran al terminar. Las muestras y resultados se generan en `build/`.
La demo no requiere ZIP ni imágenes del curso; `-Red` prefiere Radmin y prueba desde
la misma PC. La conexión entre equipos se comprueba con la
[guía de Radmin](preparacion-equipo.md#comprobación-entre-ambas-pcs).

## Herramientas de navegador

Solo son necesarias para automatizar pruebas. Instalar Node.js y Chrome; después,
con Internet disponible, ejecutar una vez:

```powershell
npm install --prefix build/browser-tools --no-save --package-lock=false playwright@1.62.1
$env:PRIB_PLAYWRIGHT = (Resolve-Path 'build/browser-tools/node_modules/playwright').Path
```

Volver a definir `PRIB_PLAYWRIGHT` en cada terminal de pruebas. No borrar
`build/browser-tools` antes de evaluar sin Internet. Las ejecuciones posteriores
usan Chrome y dependencias locales. `demo.ps1 -Node 'ruta/al/node.exe'` permite
usar Node fuera del PATH; el evaluador requiere `node` en el PATH.

Pruebas puntuales, después de `test.ps1`:

```powershell
node scripts/test-cache.cjs
node scripts/test-delta.cjs
node scripts/test-metrics.cjs
node scripts/test-sha256.cjs
node scripts/browser/preparation.cjs
node scripts/browser/sources.cjs
```

Las dos últimas usan servidores y muestras propios para verificar preparación,
subidas, URL de prueba local, errores y catálogo. No descargan imágenes públicas.
Para probar JPEG con sources, establecer antes
`$env:PRIB_SOURCE_FIXTURE = 'build/formats-fixture.json'` y eliminar la variable
al terminar con `Remove-Item Env:PRIB_SOURCE_FIXTURE`.

La suite `node scripts/browser/run.cjs` requiere un servidor abierto, la imagen
preparada de 17 GB (75471 × 75471) y una pequeña; no sirve con cualquier catálogo.
`PRIB_URL` cambia su URL y `PRIB_IMAGE` el nombre del almacén de 17 GB.
Para una comprobación reproducible sin ese catálogo, utilizar `demo.ps1`.
`PRIB_DATA` cambia el directorio del evaluador y `PRIB_BROWSER_PATH` permite otro
Chromium compatible. `PRIB_JAVA` selecciona Java para demo/evaluación.

## Verificar y consultar almacenes

Después de `test.ps1`, comprobar esquinas y centro de cada nivel, sin volver a preparar:

```powershell
java -Xmx256m -cp build/classes prib.ImageStoreTest data/imagen-28gb
java -Xmx256m -cp build/classes prib.ImageStoreTest data/imagen-55gb
```

Se esperan PASS en 11 y 12 niveles respectivamente. Para exportar una región:

```powershell
./scripts/consultar.ps1 -Store 'data/imagen-17gb' -Level 0 -X 0 -Y 0 -Width 256 -Height 256 -Output 'output/region-manual.png'
Invoke-Item './output/region-manual.png'
```

`-Store` es el almacén preparado; `-Level 0` conserva resolución original.
X/Y y Width/Height son píxeles de ese nivel. `-Output` es un **PNG nuevo**, no una
carpeta. Se espera **Región verificada y exportada**; usar otro nombre para repetir.

## Resultados e interpretación

La evaluación con el catálogo de 4, 17, 28 y 55 GB recorre regiones, repite vistas,
navega 40 regiones de la mayor imagen y prueba cuatro clientes, pausa, cancelación,
reconexión y recuperación. Necesita regiones nuevas que superen la ventana de 256 KiB.

| Ejecución | Salida |
| --- | --- |
| demo.ps1 | build/demo-stage8.json, build/demo-browser.json y capturas en build/browser-check/ |
| evaluar.ps1 | build/evaluation-stage7.json, .csv y captura |

Los nombres conservan las etapas de origen, pero los scripts ejecutan el código
actual. El catálogo de cuatro imágenes produce 85 vistas medidas. Se comprobó
navegación hasta 55 GB y cuatro clientes; las pruebas de red verificaron localhost
/IP de Radmin desde un mismo equipo, sin acreditar por sí solas conexión entre PCs.

Evidencias conservadas de ejecuciones identificadas:

- [Evaluación del 5 de octubre](mediciones/etapa-7-2026-10-05.json): 109 vistas,
  ocho imágenes hasta 55 GB, cuatro clientes y recursos.
- [Demostración sintética](mediciones/etapa-8-2026-10-05.json): modos, recuperación,
  créditos y reconexión.

Estos reportes no representan automáticamente revisiones posteriores. Las rutas de
capturas y temporales que contienen describen aquella ejecución; no son archivos entregados.

Interpretar tiempos como duración de vista, no RTT. El ahorro del evaluador compara
bytes PRIB con FULL equivalente; no compara PNG/JPEG ni toda la conexión. RGB evitado
del panel es una medida bruta. La caché de 16 MiB no representa toda la RAM del
navegador, y el muestreo de memoria puede perder picos. La navegación verifica
regiones, no cada píxel de la imagen. No se evaluaron 93 GB ni 32 clientes.
