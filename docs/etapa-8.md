# Etapa 8: documentación y demostración final

Completada el **5 de octubre de 2026**, hora de Guatemala. Se consolidó el contrato
implementado y se ejecutó una demostración offline reproducible, independiente
de los ZIP del curso. No se modificaron los algoritmos del servidor ni se volvió
a preparar ninguna imagen grande.

## Entregables

| Entregable | Contenido |
| --- | --- |
| [Protocolo PRIB v1](protocolo-prib.md) | Arquitectura, representación RGB, almacenamiento, transporte, todos los mensajes y campos, estados, prioridad, caché, generaciones, XOR_RUNS_1, recuperación, configuración, límites y referencias |
| [Guion de demostración](demostracion.md) | Ejecución offline, guion del visor grande, demo sintética automática/manual, comandos y resolución de fallos frecuentes |
| `scripts/demo.ps1`, `scripts/demonstrate.cjs` | Compilación, fixture, pruebas Java/JS, servidor temporal y ejecución Chrome; salida no cero si falla |
| `scripts/browser/demo.cjs` | Dos clientes, REUSE, agotamiento/reanudación, cancelaciones y reconexión con deuda |
| [Reporte reproducido](mediciones/etapa-8-2026-10-05.json) | Resultados de siete comprobaciones, estados finales y SHA-256 de fuentes |
| [Mediciones del sistema completo](etapa-7.md) | Evidencia histórica de seis imágenes, hasta 17 GB y cuatro clientes |

README, plan, guía de preparación y pruebas de navegador enlazan los entregables.
Se corrigió el nombre del ZIP 17/28 GB al disponible en este equipo:
`Imagenes-28G-17G-Comprimidas.zip`. Otros nombres requieren ajustar `-Zip`, sin
cambiar la entrada PNG. No se copiaron ZIP ni almacenes a los documentos.

## Validación ejecutada

1. `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\test.ps1`: todas las pruebas Java aprobadas, incluyendo PNG, almacenamiento, créditos, caché, diferencias, framing, HTTP y sesiones.
2. `scripts/demo.ps1` con Node y Playwright locales: ClientCacheTest, DeltaCodecTest, DeltaProtocolTest, test-cache.cjs y test-delta.cjs aprobados.
3. Chrome `scripts/browser/delta.cjs`: FULL → REF → DELTA → REUSE → FULL; RGB y hashes exactos, cuatro recuperaciones reales del receptor y protecciones liberadas.
4. Chrome `scripts/browser/demo.cjs`: dos sesiones, caché, WAIT_CREDIT, cliente independiente, reanudación, doce cambios rápidos y reconexión con deuda.

La demo creó una fixture de 512 × 128, con salida preparada de 236370 bytes;
FULL del bloque costó 49528 bytes y DELTA 563 bytes, payload diferencial 30 bytes.
La repetición de toda la fixture usó cuatro REUSE y 2097 bytes PRIB. Durante la
pausa se reportaron 242,9 KiB pendientes; el cliente dejó de recibir bytes mientras
otro completaba la vista. Se recibieron 19 CANCELLED en todo ese guion, incluyendo
las doce solicitudes rápidas. Al final: 4/4 bloques, caché 196608 bytes, cero
protecciones, conexión activa y ningún error JavaScript. La nueva sesión terminó
con cero deuda. Los procesos temporales se cerraron al completar la demo.

## Capturas verificadas

- [DELTA exacto](evidencias/etapa-8-delta.png): modo diferencial con SHA-256 correcto.
- [Recuperación selectiva](evidencias/etapa-8-recuperacion.png): un intento DELTA fallido y un FULL de recuperación, un objetivo verificado.
- [Después de reanudar créditos](evidencias/etapa-8-creditos.png): vista terminada, 256 KiB disponibles y cero pendientes. La pausa previa está registrada en el JSON.

Las capturas se inspeccionaron visualmente; corresponden a una fixture sintética,
no a navegación de la imagen grande. La [captura de etapa 7](evidencias/etapa-7-detalle.png)
conserva evidencia de números legibles en el nivel original de la imagen de 17 GB.

## Alcance final y limitaciones

El máximo preparado y evaluado sigue siendo la imagen de 17 GB, con cuatro
clientes simultáneos. El código limita 32 conexiones, pero no hay mediciones con
32 clientes. El documento distingue límites programados, observaciones de una
máquina y algoritmos propios de los estándares citados.

El PNG de 55 GB cumple dimensiones/formato por encabezado, pero su preparación
completa exige aproximadamente 74,68 GB libres según la reserva conservadora y
no se realizó. Tampoco se afirma validación de 28/90 GB, streaming aleatorio desde
Drive ni preparación parcial. La ejecución normal sirve localhost, sin TLS ni
autenticación. Una instalación nueva necesita herramientas y datos locales;
la demo Java no depende de Node y la demo Chrome sí requiere sus dependencias.

La etapa 8 queda cerrada por documentación y demostración comprobadas. La
presentación oral del equipo, adaptación al formato institucional y publicación
en GitHub pueden realizarse después; no se generó un commit ni se publicó una entrega.
