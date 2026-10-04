# Etapa 4 — Créditos y recuperación de sesión

> Documento del cierre de etapa 4. El protocolo actual agrega reutilización,
> caché y concesiones agrupadas; consultar [etapa 5](etapa-5.md).

## Qué se implementó

Cada sesión dispone de una ventana de bytes para recibir bloques FULL. El
servidor prepara como máximo un bloque pendiente y solo lo envía cuando cabe
en el crédito disponible. Los controles siguen funcionando durante la espera.
El visor devuelve capacidad después de procesar o descartar cada mensaje.

ACK confirma integridad y materialización; CREDIT_GRANT repone capacidad.
Son operaciones independientes. Esta etapa no incorpora REUSE, REF, DELTA,
retransmisión selectiva ni control de congestión: TCP mantiene su propio control.

## Reglas de contabilidad

El coste de un bloque es **4 + longitud de cabecera JSON UTF-8 + longitud RGB**.
No incluye controles JSON, cabeceras WebSocket, TCP/IP ni memoria del Canvas.

- Capacidad admitida: de **53.252 bytes** a **1 MiB** por sesión. El mínimo permite
  una unidad máxima: 4 + 4096 + 128 × 128 × 3 bytes.
- El visor concede **256 KiB** al iniciar la sesión.
- Se descuenta el coste exacto antes de encolar el bloque en la salida de red.
- Un mensaje preparado, pero aún no encolado, no consume crédito.
- Cambiar de vista no devuelve el crédito de los mensajes ya encolados.
- Los bloques obsoletos se descartan y devuelven capacidad al procesarse, sin
  dibujarse en la nueva vista ni confirmar materialización con ACK.
- El estado se limita a contadores; no se guarda un historial creciente de concesiones.

Sean C la capacidad inicial, S los bytes encolados y R el acumulado liberado:

```text
crédito disponible = C + R − S
bytes sin capacidad devuelta = S − R
0 ≤ crédito disponible ≤ C
0 ≤ R ≤ S
```

Puede quedar un saldo positivo insuficiente para el siguiente bloque. En ese
caso también se espera; no es necesario llegar exactamente a cero.

## Mensajes

Se conserva la versión de desarrollo PRIB 1. Los clientes deben actualizarse
para negociar créditos; un cliente antiguo ya no recibe bloques sin CREDIT_INIT.
Todos estos controles incluyen `version`, `type` y `sessionId`.

| Mensaje | Dirección y campos | Regla |
| --- | --- | --- |
| CREDIT_INIT | C → S: `capacityBytes` | Inicializa una vez. Repetir la misma capacidad no reinicia el saldo; cambiarla genera ERROR |
| CREDIT_GRANT | C → S: `grantId`, `releasedBytes` | Identificador creciente y total acumulado de bytes liberados desde el inicio de la sesión |
| CREDIT_STATUS | C → S: sin campos adicionales | Solicita el estado, incluso sin crédito |
| CREDIT_STATUS | S → C: `capacityBytes`, `availableBytes`, `outstandingBytes`, `releasedBytes`, `grantId`, `state` | Responde consultas y notifica cambios, sin repetir el mismo estado en cada vuelta |

IMAGE_INFO anuncia `maxBlockBytes` y `maxCreditBytes`. El navegador devuelve
`ArrayBuffer.byteLength` por bloque procesado, no solo la longitud de sus píxeles.
Los contadores acumulativos usan enteros de 64 bits en Java y se limitan al
máximo entero exacto de JavaScript. No se desbordan al superar 2 GiB enviados.

Una concesión con el mismo identificador y total se ignora. Una anterior cuyo
total ya está cubierto también se ignora. Una concesión nueva puede saltar
identificadores: su acumulado incorpora las anteriores. Se rechazan totales
superiores a lo enviado, decrecientes en una concesión nueva o contradictorios.
El servidor valida la contabilidad declarada; no puede comprobar la memoria
física de un cliente modificado que mienta sobre haber liberado sus buffers.

Estados: `WAIT_INIT` (sin capacidad inicial), `WAIT_CREDIT` (saldo insuficiente),
`SENDING` (trabajo pendiente) e `IDLE` (sin envíos pendientes). IDLE no significa
que todos los ACK o devoluciones de capacidad ya hayan llegado.

## Recuperación básica

**Reconectar** crea otra sesión, vuelve a negociar la capacidad y solicita la
región que estaba seleccionada. El saldo y las concesiones de la sesión anterior
no se trasladan. Las verificaciones asíncronas viejas no modifican la sesión nueva.
La reconexión es manual; no hay reintentos automáticos indefinidos.

Un error de integridad o de protocolo cierra la conexión. En esta etapa se puede
reconectar y solicitar la vista completa otra vez; la recuperación selectiva de
bloques y bases DELTA se implementará posteriormente. CREDIT_STATUS permite
consultar el saldo sin consumir crédito; una concesión acumulativa posterior
incluye devoluciones anteriores, sin necesidad de conservarlas individualmente.

## Prueba manual

Iniciar con `./scripts/servidor.ps1` y abrir http://localhost:8080. No hace falta
preparar de nuevo los almacenes existentes.

1. Elegir la imagen de 17 GB, abrir **Mostrar transferencia** y usar nivel 0.
2. Esperar Vista completa. El crédito debe volver a **256.0 KiB** y los bytes
   sin capacidad devuelta a **0.0 KiB**.
3. Pulsar **Pausar devoluciones** y desplazarse a otra región amplia. El servidor
   enviará lo que aún cabe y terminará en **Esperando crédito**. Con mucho zoom
   puede caber toda la vista: usar 100 % y una ventana amplia para esta prueba.
4. Cambiar de región durante la espera. La capacidad pendiente sigue contabilizada.
5. En una segunda pestaña, comprobar que se puede seguir navegando normalmente.
6. Pulsar **Reanudar devoluciones**. Debe completarse la última región solicitada
   con SHA-256 correcto y recuperarse la capacidad inicial.
7. Repetir la pausa y pulsar **Reconectar**. Debe cambiar la sesión, conservarse
   la región y completarse la vista con una ventana nueva, sin deuda anterior.

Pausar no revoca lo ya concedido ni detiene ACK. Es una herramienta de demostración:
los bloques se procesan normalmente, pero se retiene la concesión de capacidad.
Solo se acumula un contador, no los datos de los bloques. Los créditos son por
sesión; las métricas de RGB siguen correspondiendo a la vista actual.

## Código y comprobaciones

- `CreditWindow.java`: contabilidad, límites y concesiones acumulativas.
- `PribSession.java`: bloque preparado pendiente, descuento al enviar y estados.
- `web/app.js`: negociación, liberación después de procesar, pausa y reconexión.
- `CreditWindowTest.java`: límites, cero exacto, duplicados, unidad máxima y más de 2 GiB.
- `PribServerTest.java`: clientes WebSocket reales, saldo por bytes completos,
  agotamiento, reanudación, separación de ACK, INIT repetido, cambios de vista,
  concesiones atrasadas, cliente lento y sesión nueva.

Ejecutar `./scripts/test.ps1`: deben pasar tanto estas pruebas como las anteriores.
También se verificó en Chrome con el almacén de 17 GB: pausa y reanudación,
concesiones duplicadas, cambio de región detenido, dos clientes y reconexión.
Las comprobaciones del visor incluyeron zoom extremo, bordes, pantalla completa
y una ventana 4K con 496 bloques verificados.

La ventana controla bytes PRIB pendientes, no toda la memoria del proceso.
Se mantienen los límites anteriores de conexiones, colas, tiempo y trabajadores.
La planificación avanzada, caché y recuperación selectiva quedan pendientes.

## Mantenimiento posterior a la revisión

Se consolidó el CSS y se extrajeron funciones pequeñas para actualizar controles,
contadores y dibujar bloques verificados. El bloque pendiente se representa como
una sola unidad (identificador, hash y bytes). Cada escritura de red respeta ahora
el presupuesto restante del turno, incluso cuando el buffer completo es mayor.
Una prueba cubre el límite, las escrituras parciales y la restauración tras error.
Las pruebas de navegador se conservan en `scripts/browser/`; ver
[preparación y ejecución](pruebas-navegador.md). Estos ajustes mantienen el alcance
de las etapas 1 a 4 y no implementan funciones de etapa 5.
