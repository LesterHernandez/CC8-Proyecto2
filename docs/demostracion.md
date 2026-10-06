# Demostración y ejecución offline

Guion de etapa 8, 5 de octubre de 2026. Documento técnico:
[protocolo-prib.md](protocolo-prib.md). La demostración usa recursos locales;
los enlaces bibliográficos no se necesitan para ejecutar.

## 1. Ejecutar lo que ya está preparado

Requisitos del visor: JDK 21 (`java`, `javac`), PowerShell, navegador con WebSocket,
Canvas y Web Crypto, carpeta `web/` y al menos un almacén completo en `data/`.
No requiere Maven, Node, Playwright ni conexión a internet. Otra computadora debe
recibir el código y los almacenes, o preparar sus propios ZIP: `data/` no viene en Git.

Abrir PowerShell en la raíz del proyecto:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\servidor.ps1
```

Abrir **http://localhost:8080** y mantener la terminal abierta. Esta opción de
ExecutionPolicy se aplica al proceso iniciado, sin cambiar la política permanente.
Si una política administrada por el equipo impide esa ejecución, usar el comando
Java descrito más abajo. Para otro puerto, añadir `-Port 8081` y abrir localhost:8081.
Detener con Ctrl+C. Después de actualizar código, reiniciar y usar Ctrl+F5.

El cliente es cada pestaña del navegador, con caché y créditos propios. El servidor
es el proceso Java. `preparar.ps1` es una herramienta previa; borrar `data/` elimina
las imágenes disponibles. Para una prueba temporal, detener el servidor y conservar
el almacén bajo otro nombre en vez de borrarlo.

Alternativa sin ejecutar un archivo `.ps1`, desde PowerShell en la raíz:

```powershell
New-Item -ItemType Directory -Force build/classes | Out-Null
$sources = Get-ChildItem src/main/java/prib/*.java | Select-Object -ExpandProperty FullName
javac -encoding UTF-8 -d build/classes $sources
# Continuar únicamente si la compilación termina correctamente.
java -Xmx256m -cp build/classes prib.PribServer 8080 data
```

## 2. Guion del visor sobre la imagen grande (5–8 minutos)

| Paso | Acción | Resultado que debe mostrarse |
| --- | --- | --- |
| Arquitectura | Enseñar las carpetas `src`, `web`, `data` | Cliente, servidor y preparación separados |
| Navegación | Seleccionar imagen-17gb; acercar al nivel 0 y después al 400 % | Números legibles; solo se solicita la región visible |
| Integridad | Abrir Mostrar transferencia y esperar Vista completa | Bloques verificados/esperados iguales, SHA-256 correcto |
| REUSE | Pulsar Ir a la región sin cambiar coordenadas | Aumenta REUSE y baja el coste PRIB de la vista |
| Concurrencia | Abrir otra pestaña y elegir otra región | Sesiones distintas y vistas independientes |
| Crédito | Primera pestaña: Pausar devoluciones y pedir una región nueva grande | Esperando crédito; la segunda pestaña sigue progresando |
| Reanudar | Reanudar devoluciones | Vista completa y pendiente de crédito vuelve a cero |
| Generaciones | Arrastrar o cambiar región rápidamente | Solo se dibuja la generación final, sin bloques antiguos superpuestos |
| Reconexión | Reconectar | Nuevo sessionId; región conservada, caché y deuda de sesión anterior eliminadas |

El inventario puede producir REF/DELTA en imágenes reales, pero no se garantiza
un modo concreto en una región arbitraria. Para mostrarlos de forma determinista
usar la fixture de la siguiente sección. No atribuir un ensayo sintético al tamaño
de la imagen grande.

## 3. Demostración automática pequeña y determinista

Requisitos adicionales: Node, Chrome y Playwright **ya instalados localmente**.
No se descargan paquetes durante el comando. Si Playwright está fuera del árbol
de módulos de Node, establecer `$env:PRIB_PLAYWRIGHT` a la ruta local de ese paquete.
`PRIB_BROWSER_PATH` permite indicar otro ejecutable Chromium compatible;
`PRIB_JAVA` permite indicar el comando Java usado para descubrir el JDK real.
El parámetro `-Node` de demo.ps1 permite usar un Node que no está en PATH.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1
```

El comando compila código y pruebas, verifica caché y encoder, crea un ZIP sintético
y un almacén 512 × 128 en `build/`, prueba PRIB por WebSocket y ejecuta dos guiones
Chrome. No requiere ZIP del curso ni `data/`. El servidor se inicia en puerto libre
y se detiene al terminar o fallar; no se detienen servidores del usuario. Se crean
unos pocos cientos de KiB de almacén, además de clases, capturas y reporte; no se
prepara la imagen de 55 GB. Cada ejecución crea una fixture nueva en `build/`.

| Objetivo | Evidencia automática |
| --- | --- |
| FULL | Primera base y bloque independiente transmitidos como RGB RAW |
| REF | Otro bloque con RGB y geometría idénticos usa BLOCK_REF/REF |
| DELTA | Objetivo con dos píxeles cambiados usa XOR_RUNS_1 y reconstruye hash exacto |
| REUSE | Volver a la identidad retenida usa BLOCK_REF/REUSE |
| Recuperaciones | BASE_MISSING, HASH_MISMATCH, DELTA_FAILED y CACHE_MISS provocados en el receptor; cada objetivo se recupera con un FULL |
| Límites | Candidatos acotados, base protegida, presupuesto de caché, dos reintentos máximos |
| Créditos | Pausa hasta WAIT_CREDIT; bytes detenidos; otro cliente termina; reanudar recupera saldo |
| Generaciones | Doce cambios rápidos; final correcto y protecciones liberadas |
| Reconexión | Nueva sesión con deuda anterior; final sin deuda y otro cliente conectado |

Resultados locales: `build/demo-stage8.json`, `build/demo-browser.json` y capturas
en `build/browser-check/`. La salida debe terminar en `PASS demostración completa`.
Un fallo devuelve código distinto de cero; no se presenta como demostración exitosa.
Las inyecciones solo afectan a los buffers de prueba del navegador, no a los packs.

Sin Node/Playwright, las pruebas Java siguen comprobando los cuatro modos y RECOVER:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test.ps1
```

Esto no sustituye la validación automática de Chrome, pero permite mostrar el
backend offline en un equipo que solo tenga JDK y PowerShell.

## 4. Mostrar los modos manualmente con la fixture

Después de demo.ps1 o test.ps1, localizar el almacén recién generado e iniciar
su propio servidor en otro puerto:

```powershell
$fixture = Get-ChildItem .\build -Directory -Filter 'delta-test-*' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
java -Xmx256m -cp build/classes prib.PribServer 8082 "$($fixture.FullName)\data"
```

Abrir localhost:8082, Mostrar transferencia y la consola del navegador. La vista
inicial carga toda la fixture; por eso hay que limpiar el inventario antes de la
secuencia. Ejecutar y esperar Vista completa:

```javascript
cache.invalidate(current.imageId, '', true);
level = 0; magnification = 16; x = 0; y = 0; requestView();
```

Debe aparecer un FULL. Ejecutar las siguientes líneas **una por una**, esperando
Vista completa entre ellas:

```javascript
x = 128; requestView(); // REF: copia exacta con otra identidad
x = 256; requestView(); // DELTA: dos píxeles distintos
x = 0; requestView();   // REUSE: la base original retenida
x = 384; requestView(); // FULL: contenido independiente
```

La ampliación 1600 % hace que cada región solicitada caiga dentro de un bloque.
El contenido sintético es ruido reproducible con semilla 6106, no números del curso.
FULL cuesta alrededor de 49528 bytes; DELTA alrededor de 562–563, dependiendo de
IDs de cabecera. El ahorro cercano a 98,9 % corresponde solo a esa fixture.

## 5. Informe y mediciones para exponer

Mostrar [protocolo consolidado](protocolo-prib.md) y
[mediciones de etapa 7](etapa-7.md), con JSON/CSV versionados. Explicar:

1. ACK verifica, CACHE_STATE declara retención y CREDIT_GRANT devuelve capacidad.
2. FULL equivalente incluye cabecera y RGB; RGB evitado del panel es una medida bruta distinta.
3. CANCEL no retira bytes que ya viajaban; el cliente los descarta y procesa sus créditos.
4. DELTA usa base confirmada, diferencia exacta y SHA-256; una firma parecida no garantiza ahorro.
5. El límite del código es 32 conexiones, pero se probaron cuatro clientes con 17 GB.
6. La imagen de 55 GB es compatible por encabezado; no está preparada ni evaluada completa.

Para repetir la evaluación grande opcional:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\evaluar.ps1
```

Requiere las seis imágenes preparadas y dependencias de navegador locales. Guarda
nuevos datos en `build/` sin reemplazar las mediciones históricas de `docs/`.

## 6. Fallos frecuentes

| Síntoma | Acción |
| --- | --- |
| Script sin firma digital | Usar la invocación PowerShell con ExecutionPolicy Bypass para ese proceso |
| Puerto ocupado | Elegir otro puerto; no cerrar un servidor ajeno sin identificarlo |
| `java` o `javac` no disponibles | Configurar JDK 21 y abrir otra terminal |
| No hay imágenes preparadas | Restaurar `data/` o preparar un ZIP; la demo pequeña no depende de `data/` |
| Espacio insuficiente | Elegir una unidad con espacio suficiente; no quitar la reserva para forzar 55 GB |
| Cambios no visibles | Reiniciar servidor y Ctrl+F5 |
| No se encuentra Playwright/Chrome | Usar rutas locales o las pruebas Java; el visor normal no necesita estas dependencias |
| Conexión cerrada por ERROR | Leer el mensaje; reconectar crea otra sesión, no repara un almacén corrupto |

La entrega debe incluir código, estos documentos y evidencias, sin subir ZIP ni
packs gigantes. Los almacenes se distribuyen aparte o se generan con la guía de
[preparación del equipo](preparacion-equipo.md). La ejecución normal es offline
cuando herramientas y datos ya están presentes; la instalación inicial y obtener
los ZIP pueden requerir internet.
