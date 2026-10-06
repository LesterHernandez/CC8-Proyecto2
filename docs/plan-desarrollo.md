# Plan de desarrollo de PRIB

Este documento guía los avances del equipo según la propuesta PRIB y las
indicaciones del curso. El objetivo final es servir imágenes grandes con
reutilización, transmisión diferencial exacta, prioridades, créditos, caché
y recuperación por cliente, sin transferir la imagen completa al navegador.

Las etapas son incrementales. Su cierre depende de evidencia funcional,
no solo de haber escrito el código. La documentación se actualiza durante
todo el desarrollo; la etapa 8 integra y prepara la entrega final.

## Estado general

Mejora de usabilidad incorporada: preparación de ZIP desde la web con progreso,
una tarea a la vez y actualización automática del catálogo. Se conserva la
alternativa por terminal. El catálogo de trabajo actual contiene 4, 17, 28 y 55 GB;
la evaluación histórica de ocho imágenes sigue documentada en las pruebas.

| Etapa | Resultado | Estado |
| --- | --- | --- |
| 1 | Base técnica y bloques verificables | Implementada y probada en imágenes pequeñas |
| 2 | Preprocesamiento y almacenamiento de imágenes | Implementada y probada hasta la imagen de 55 GB |
| 3 | Servidor y primera comunicación completa | Implementada; FULL y dos clientes probados con almacén de 17 GB |
| 4 | Créditos y recuperación básica | Implementada; agotamiento, reanudación y sesiones independientes verificados |
| 5 | Reutilización, prioridades, caché y generaciones | Implementada; REUSE/REF/FULL, caché limitada, prioridades y CANCEL verificados |
| 6 | DELTA exacto y recuperación selectiva | Implementada; cuatro modos, firmas acotadas y RECOVER a FULL verificados |
| 7 | Evaluación con imágenes grandes y concurrencia | Implementada; ocho imágenes, hasta 55 GB, cuatro clientes, recursos y recuperación medidos |
| 8 | Documentación y demostración final | Demostración y documento locales verificados; pendiente integrar la actualización de preparación web en esos entregables |

## 1. Base técnica y prueba de bloques

**Trabajo:** revisar formatos, leer PNG por filas desde ZIP, definir RGB canónico,
dividir imágenes pequeñas y verificar píxeles y hashes. Documentar la elección
inicial Java 21 y PRIB sobre WebSocket/TCP para las etapas de comunicación.

**Cierre:** bloques y bordes iguales a ImageIO, errores PNG detectados y contrato
interpretado en JavaScript. Evidencia en [protocolo y arquitectura](protocolo.md). La etapa 1 no incluye lectura completa de imágenes grandes ni el canal de navegador.

## 2. Preparación y almacenamiento de imágenes

**Trabajo:** separar el preprocesador del verificador pequeño; generar bloques
comprimidos, niveles de resolución e índice de acceso. Permitir nuevas imágenes
con el mismo procedimiento y conservar detalle original en el nivel máximo.
Evitar cargar imágenes completas o generar cantidades inmanejables de archivos.

**Cierre:** consultar regiones y bordes por bloque; registrar memoria, tiempo,
espacio de salida y temporales. Probar tamaños crecientes antes de preparar
las imágenes de 17 y 28 GB. Los números deben conservarse legibles a máxima resolución.

Implementación y comandos en [protocolo y arquitectura](protocolo.md).

## 3. Servidor y primera comunicación completa

**Trabajo:** servidor Java asíncrono con HTTP inicial, WebSocket y frontend local
sencillo. Implementar HELLO, IMAGE_INFO, VIEW, BLOCK_FULL y ACK, identificadores
de sesión/vista/transferencia, límites de mensajes y cierre de recursos.

**Cierre:** dos navegadores solicitan regiones diferentes con estados independientes;
reciben, verifican y dibujan únicamente los bloques solicitados. Las operaciones
de disco o CPU no bloquean la atención de conexiones; las colas tienen límites.

Implementación, mensajes y pruebas en [protocolo y arquitectura](protocolo.md).

## 4. Créditos y recuperación básica

**Trabajo:** CREDIT_INIT, CREDIT_GRANT y CREDIT_STATUS; contabilizar bytes PRIB,
concesiones duplicadas y capacidad en tránsito. Mantener control esencial activo
sin créditos de datos y separar ACK de devolución de capacidad.

**Cierre:** crédito agotado detiene datos; nueva capacidad reanuda la transmisión.
No hay crédito negativo ni concesiones duplicadas. Una unidad máxima puede
transmitirse bajo la configuración negociada y un cliente lento no detiene a otro.

Implementación, reglas y pruebas en [protocolo y arquitectura](protocolo.md).

## 5. Reutilización, prioridades, caché y generaciones

**Trabajo:** REUSE y REF, inventario CACHE_STATE, prioridad por valor visual/coste,
caché con presupuesto y utilidad, reparto justo entre clientes y CANCEL por viewId.
Proteger bases activas y contabilizar los datos que siguen en tránsito.

**Cierre:** volver a una región reutiliza contenido; movimientos rápidos cancelan
trabajo obsoleto sin dibujar bloques incorrectos ni liberar créditos antes de tiempo.
Memoria limitada y progreso de bloques elegibles. Completa el nivel A de la propuesta.

Implementación, contrato y pruebas en [protocolo y arquitectura](protocolo.md).

## 6. DELTA exacto y recuperación selectiva

**Trabajo:** probar una técnica diferencial lossless, firmas de similitud e índice
con candidatos limitados. Elegir una base confirmada y comparar coste total de
DELTA frente a FULL. Verificar SHA-256, proteger dependencias y limitar reintentos.

**Cierre:** demostrar REUSE, REF, DELTA y FULL. Provocar BASE_MISSING, HASH_MISMATCH,
DELTA_FAILED y desajustes de caché, recuperando el bloque correcto. Usar FULL si
DELTA no ahorra lo suficiente. Sin cadenas pendientes de reconstrucción.

Implementación, formato diferencial y pruebas en [protocolo y arquitectura](protocolo.md).

## 7. Imágenes grandes, recursos y concurrencia

**Trabajo:** ejecutar el sistema completo sobre imágenes disponibles con varios
clientes. Medir bytes por modo, ahorro frente a FULL equivalente, latencia de vista,
memoria, disco, créditos, cancelaciones y recuperaciones. Ajustar políticas según
mediciones, incluyendo clientes lentos, desconexiones y navegación prolongada.

**Cierre:** imagen objetivo navegable con números legibles, recursos acotados y
resultados reproducibles sin internet. La imagen de 24 GB es referencia de prueba
inicial del curso, no meta final; el tamaño máximo demostrado se reportará honestamente.

Mediciones, límites y reproducción en [pruebas y resultados](pruebas.md), incluidos 28 y 55 GB.

## 8. Documentación y demostración final

**Trabajo:** consolidar arquitectura, mensajes y campos, estados, algoritmos,
parámetros, políticas, referencias técnicas y mediciones. Preparar instrucciones
de ejecución sin internet y una demostración de los mecanismos del protocolo.

**Cierre:** otro integrante puede ejecutar y explicar el proyecto con lo documentado;
la demostración muestra los cuatro modos, créditos, caché y recuperación. El informe
coincide con el código y declara las limitaciones verificadas. Referencia de evaluación:
20 % frontend, 20 % backend y 60 % documento del protocolo, según la aclaración recibida.

## Cómo registrar los avances en GitHub

1. Terminar el alcance previsto y ejecutar las comprobaciones de esa etapa.
2. Actualizar este estado y las secciones correspondientes de `docs/protocolo.md` y `docs/pruebas.md`.
3. Actualizar el README si cambian requisitos, comandos o funcionalidad disponible.
4. Guardar el avance en un commit descriptivo, por ejemplo `Etapa 1: bloques RGB verificables`.
5. Opcionalmente marcar el hito con una etiqueta como `etapa-1`; pueden existir varios
   commits de correcciones dentro de la misma etapa.

Los nombres anteriores son una convención propuesta, no commits o etiquetas ya
creados. Los ZIP, datos generados y binarios no se incluyen en los avances.
Mantener visibles las etapas pendientes evita presentar como implementadas
funciones que todavía son parte del diseño.

Los entregables locales previos a esta actualización se conservan en
[protocolo-prib.md](protocolo-prib.md), [demostracion.md](demostracion.md)
y [etapa-8.md](etapa-8.md), junto con el Word. La referencia actual del código
es [protocolo.md](protocolo.md).
