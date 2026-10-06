# Demostración de PRIB

Este guion permite mostrar las funcionalidades del proyecto. La instalación,
preparación e inicio están en el [README](../README.md) y la [guía del equipo](preparacion-equipo.md).
Para automatizar escenarios, configurar las [herramientas de navegador](pruebas.md#herramientas-de-navegador).

## Recorrido con una imagen grande

Abrir el visor y elegir una imagen preparada de 17, 28 o 55 GB.

| Paso | Acción | Resultado observable |
| --- | --- | --- |
| Región visible | Acercar al nivel 0 y luego al 400 %; arrastrar | Números ampliados y solicitudes limitadas a la región visible |
| Integridad | Abrir Mostrar transferencia y esperar | Vista completa, bloques coincidentes y SHA-256 correcto |
| Reutilización | Pulsar Ir a la región sin cambiar coordenadas | REUSE si los bloques permanecen en caché; menos bytes que FULL equivalente |
| Concurrencia | Abrir otra pestaña en otra región | Sesiones y navegación independientes |
| Créditos | Al 100 %, abrir Mediciones y demostración, pausar devoluciones y pedir una región nueva | Esperando crédito y espera acumulada; la otra pestaña sigue progresando |
| Reanudación | Reanudar devoluciones | Completa la vista y la capacidad devuelta alcanza los bytes procesados |
| Generaciones | Arrastrar o cambiar coordenadas rápidamente | Solo se dibuja la última vista; no se mezclan bloques antiguos |
| Reconexión | Reconectar | Cambia la sesión, se conserva la región y se reinician caché y deuda |
| Preparación web | Preparar una imagen pequeña con nombre nuevo | Progreso y actualización del catálogo en las pestañas sin reiniciar |

Con mucho zoom o reutilización, la vista puede caber completa en el crédito
restante y no detenerse: para mostrar la pausa usar una región nueva amplia.
REF y DELTA dependen del contenido y las bases; no se garantiza que aparezcan
en cualquier zona de una imagen real. Utilizar la muestra siguiente para enseñarlos.

El panel separa **Esta vista** (bloques, modos, bytes e integridad) de **Esta sesión**
(caché y créditos). La sesión abreviada coincide con la terminal; el identificador
completo aparece al mantener el cursor sobre ella. Los cuatro modos cuentan
transmisiones, incluidos reintentos, no necesariamente bloques únicos.
**Verificando bloques** cambia a **SHA-256 correcto** al completar la vista.
El desplegable conserva tiempos, payload, ahorro bruto y controles de demostración.

### Seguir a los clientes en la terminal

Los logs aparecen al iniciar normalmente, también con `-Red`. Cada línea contiene
hora y sesión abreviada: CONECTADO/DESCONECTADO, VISTA, COMPLETA, CREDITO,
RECUPERACION, CANCELADA o ERROR. COMPLETA resume confirmados, FULL/REUSE/REF/DELTA,
bytes PRIB, recuperaciones y duración hasta confirmar la vista en el servidor.
Este tiempo no es RTT y puede diferir del medido en el navegador.

Al arrastrar rápidamente se agrupan eventos: `agrupados=N último: ...` informa
cuántos se resumieron y el detalle más reciente. No se imprimen bloques, ACK ni
hashes individuales. CANCELADA representa una vista aún en curso; cambiar después
de completarla no se presenta como trabajo descartado. Los créditos se registran
al entrar o salir de espera, no por cada devolución. Todo funciona localmente,
sin servicios externos ni archivos de log permanentes.

## Demostración automática de los cuatro modos

Con las [herramientas de navegador](pruebas.md#herramientas-de-navegador) instaladas:

```powershell
./scripts/demo.ps1
```

Genera una muestra de 512 × 128 y ejecuta FULL → REF → DELTA → REUSE → FULL,
cuatro causas de recuperación, créditos, cancelación y dos clientes. No requiere
imágenes del curso ni Internet. Se espera **PASS demostración completa**;
los resultados están en `build/demo-stage8.json` y las capturas en `build/browser-check/`.
Los fallos se inyectan en el receptor y no alteran las imágenes del equipo.

## Mostrar cada modo manualmente

Después de demo.ps1 o test.ps1, iniciar el servidor sobre la muestra recién generada:

```powershell
$fixture = Get-ChildItem ./build -Directory -Filter 'delta-test-*' |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
./scripts/servidor.ps1 -Port 8082 -Data "$($fixture.FullName)/data"
```

Abrir `http://localhost:8082`, **Mostrar transferencia** y la consola del navegador.
Para aislar el primer bloque, ejecutar:

```javascript
cache.invalidate(current.imageId, '', true);
level = 0; magnification = 16; x = 0; y = 0; requestView();
```

Esperar Vista completa: debe verse un FULL. Luego ejecutar estas líneas **una a la
vez**, esperando Vista completa entre ellas:

```javascript
x = 128; requestView(); // REF: misma geometría y contenido, otra identidad
x = 256; requestView(); // DELTA: reconstrucción exacta de dos píxeles distintos
x = 0; requestView();   // REUSE: identidad original conservada
x = 384; requestView(); // FULL: contenido independiente
```

Usar una ventana de escritorio habitual (por ejemplo, 1440 × 1000). Si al 1600 %
la región supera 128 píxeles de ancho, ampliar más para aislar un bloque. La muestra
es ruido reproducible, no los números de las imágenes del curso. Su elevado ahorro
DELTA no representa el ahorro de todas las imágenes reales.

## Puntos clave

ACK confirma integridad, CACHE_STATE informa retención y CREDIT_GRANT devuelve
capacidad. Todos los modos verifican RGB; cancelar no retira bytes que ya viajaban.
La caché de 16 MiB no es toda la RAM del navegador. La muestra sintética permite
reproducir modos, pero su ahorro no representa cualquier imagen real.
Consultar [protocolo](protocolo.md) y [resultados](pruebas.md#resultados-e-interpretación).
