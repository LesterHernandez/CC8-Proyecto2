# Plan de desarrollo de PRIB

Este documento describe el orden de implementación del Protocolo de Reutilización
Inteligente de Bloques (PRIB), basado en la propuesta aprobada y las aclaraciones
del curso. Permite que ambos colaboradores compartan el alcance, las tareas y los
criterios para cerrar cada etapa.

**Estado actual: etapa 1 completada; etapas 2 a 8 pendientes.**
La guía para compilar y ejecutar está en el [README principal](../README.md).

## Objetivo y alcance

Construir un servidor asíncrono Java 21 que permita explorar imágenes grandes
mediante transferencia selectiva, mantenga sesiones independientes y limite el
consumo de recursos del servidor y del navegador. Todo deberá funcionar sin
internet y todos los recursos serán servidos por el servidor Java.

El núcleo de PRIB combina cuatro decisiones de contenido:

- **REUSE:** el cliente ya tiene el bloque solicitado.
- **REF:** el cliente tiene contenido idéntico con otro identificador.
- **DELTA:** reconstruir exactamente el bloque a partir de una base verificada y
  una diferencia sin pérdida, cuando ahorre lo suficiente frente a FULL.
- **FULL:** enviar el contenido completo de un bloque, nunca toda la imagen grande.

Estas decisiones se complementan con prioridad visual y coste, créditos de
recepción, caché limitada, cancelación por generaciones y recuperación de errores.
La división en bloques y los niveles de detalle son infraestructura del protocolo.

## Orden de trabajo

| Etapa | Resultado principal | Estado |
| --- | --- | --- |
| 1. Preparación y decisiones | Base ejecutable y lectura regional con memoria acotada | Completada |
| 2. Preprocesamiento | Bloques direccionables, metadatos y niveles | Pendiente |
| 3. PRIB mínimo | Transferencia FULL de extremo a extremo | Pendiente |
| 4. Créditos | Control de recepción y límites por cliente | Pendiente |
| 5. Navegación y prioridades | Vista vigente, planificación y cancelación | Pendiente |
| 6. Caché y reutilización | REUSE y REF con memoria limitada | Pendiente |
| 7. Diferencias y recuperación | DELTA exacto y recuperación selectiva | Pendiente |
| 8. Escala y entrega | Pruebas reales, ajustes y documentación final | Pendiente |

Las etapas 1 a 6 construyen la base funcional del nivel A de la propuesta;
la etapa 7 incorpora el nivel B y la etapa 8 desarrolla el nivel C.
DELTA es parte del alcance final, aunque se implemente después de FULL.

## Etapa 1 — Preparación y decisiones técnicas

Tareas completadas:

- Revisar Java, memoria, almacenamiento y las imágenes del ZIP.
- Crear estructura Java 21, scripts de compilación y pruebas sin dependencias externas.
- Registrar WebSocket sobre TCP como elección de diseño para PRIB y HTTP inicial.
- Definir muestras RGB y hash SHA-256 con versión, formato y dimensiones.
- Leer regiones PNG con memoria acotada y comentar el código en español.

**Criterio de cierre:** compilar y ejecutar, y obtener una región real sin cargar
la imagen completa en memoria. Se aprobaron 22 comprobaciones y una lectura de
la esquina inferior derecha del PNG de 4.06 GB con heap máximo de 64 MiB.

Consultar las [decisiones](decisiones-etapa-1.md) y la
[validación de etapa 1](validacion-etapa-1.md). La red aún no está implementada.

## Etapa 2 — Preprocesamiento de imágenes

Tareas:

- Diseñar un recorrido de la imagen con buffers acotados; evitar decodificar de
  nuevo desde el principio por cada bloque.
- Guardar bloques direccionables y metadatos de imagen, ubicación, dimensiones,
  nivel, tamaño y hash.
- Generar los niveles de detalle necesarios y preservar las muestras originales
  en el nivel de máxima resolución.
- Definir cómo incorporar otra imagen sin cambiar el código.
- Controlar memoria temporal, espacio de salida y resultados incompletos.

**Criterio de cierre:** recuperar bloques por identificador, reconstruir regiones
incluyendo bordes y verificar fidelidad en resolución original. Medir tiempo,
memoria y disco al avanzar de muestras pequeñas a las grandes disponibles.

## Etapa 3 — PRIB mínimo de extremo a extremo

Tareas:

- Implementar el servidor asíncrono y servir los recursos web localmente.
- Fijar el formato de mensajes, cabeceras, longitudes, límites y errores antes de
  programar el intercambio. La elección actual es PRIB sobre WebSocket.
- Crear sesiones independientes y una interfaz básica para mostrar regiones.
- Implementar HELLO, IMAGE_INFO, VIEW, BLOCK_FULL, ACK y cierre de sesión.
- Verificar el hash antes de aceptar cada bloque y registrar bytes y tiempos básicos.

**Criterio de cierre:** dos clientes muestran bloques reales sin compartir por
error el estado de sesión. El navegador obtiene todos los recursos del servidor
Java y nunca descarga la imagen grande completa.

## Etapa 4 — Créditos y límites de recursos

Tareas:

- Implementar CREDIT_INIT, CREDIT_GRANT y CREDIT_STATUS.
- Contabilizar bytes autorizados por sesión y evitar aplicar concesiones duplicadas.
- Definir la resincronización y el tamaño máximo que puede transmitirse.
- Separar capacidad de recepción/procesamiento de memoria de caché.
- Mantener disponibles los controles esenciales cuando no hay crédito de datos.
- Limitar colas y buffers; un cliente lento no debe monopolizar recursos.

**Criterio de cierre:** el servidor pausa datos nuevos si el crédito es insuficiente
 y reanuda cuando corresponde. Probar crédito cero, duplicados, discrepancias y
clientes con capacidades diferentes. ACK y devolución de capacidad son eventos distintos.

## Etapa 5 — Navegación, prioridades y cancelación

Tareas:

- Permitir desplazamiento y selección de detalle según la vista actual.
- Aplicar CVP: visibilidad, proximidad, detalle, ganancia visual y coste estimado.
- Introducir viewId, VIEW_UPDATE y CANCEL.
- Reutilizar o repriorizar trabajo útil y cancelar el que quedó obsoleto.
- Gestionar los datos ya enviados sin devolver crédito mientras ocupen capacidad.
- Mantener planificación justa entre sesiones.

**Criterio de cierre:** la vista vigente recibe atención primero. Los cambios rápidos
no permiten que resultados antiguos sustituyan la vista actual y las colas permanecen
acotadas. Registrar trabajo cancelado y tiempo para completar regiones visibles.

## Etapa 6 — Caché y reutilización exacta

Tareas:

- Implementar caché limitada por memoria y CACHE_STATE.
- Implementar REUSE y REF, distinguiendo identidad de bloque e identidad de contenido.
- Aplicar valor de caché por recencia, proximidad, frecuencia y potencial de reutilización.
- Notificar expulsiones y proteger bases que tengan dependencias activas.
- Medir aciertos, memoria y bytes evitados.

**Criterio de cierre:** volver a una región o encontrar contenido idéntico evita
transferencias de píxeles innecesarias. Llenar la caché provoca expulsiones coherentes
con el servidor, sin superar el presupuesto ni eliminar bases protegidas.

## Etapa 7 — DELTA y recuperación selectiva

Tareas:

- Elegir una firma compacta y limitar la búsqueda de candidatos mediante un índice.
- Implementar una técnica diferencial sin pérdida compatible con Java y JavaScript.
- Usar bases materializadas y verificadas, evitando cadenas profundas.
- Comparar bytes de DELTA más control contra FULL y aplicar un margen de ahorro.
- Verificar el hash reconstruido antes de confirmar el bloque.
- Recuperar BASE_MISSING, HASH_MISMATCH, DELTA_FAILED, CACHE_MISS y expiración.
- Acotar reintentos y recurrir a FULL cuando corresponda.

**Criterio de cierre:** demostrar reconstrucción exacta, casos con ahorro medible y
casos donde se elige FULL. Provocar fallos de base/hash y comprobar recuperación
sin bucles, dependencias abandonadas ni créditos inconsistentes.

## Etapa 8 — Escala, ajustes y entrega

Tareas:

- Aumentar progresivamente tamaño de imágenes y cantidad de clientes.
- Probar las imágenes oficiales de evaluación cuando estén disponibles.
- Ajustar pesos, caché, créditos, concurrencia y umbrales usando mediciones.
- Comprobar legibilidad de números en máxima resolución.
- Consolidar documentación de campos, estados, algoritmos, políticas y referencias.
- Preparar instrucciones de ejecución y una demostración reproducible sin internet.

**Criterio de cierre:** demostrar REUSE, REF, DELTA y FULL, fidelidad, créditos,
prioridad, cancelación, recuperación y límites de recursos con evidencias. La imagen
de 24 GB es la base para comenzar la evaluación, no su puntaje final.

Según el correo: 17, 28, 55 y 93 GB tienen máximos de 20, 40, 80 y 115 puntos,
respectivamente. La aclaración posterior distribuye la evaluación en frontend
20 %, backend 20 % y documento del protocolo 60 %. Las pruebas actuales con
4 GB no acreditan todavía capacidad para los tamaños de evaluación.

## Cómo coordinaremos el trabajo

1. Antes de comenzar una tarea, acordar quién la realiza, su alcance y los archivos
   que tocará. Evitar editar simultáneamente el mismo módulo.
2. Trabajar en cambios pequeños dentro de la etapa activa. Si dos tareas dependen
   de un contrato compartido, acordarlo primero y después repartirlas.
3. Incluir comentarios en español que expliquen intención, decisiones y lógica
   importante. Mantenerlos actualizados al modificar el comportamiento.
4. Ejecutar las comprobaciones pertinentes y documentar el resultado antes de
   integrar un cambio. Quien lo revise debe poder reproducirlo.
5. Cerrar cada etapa con su criterio de aceptación y actualizar el estado en este
   plan y en el README principal. Registrar fallos o límites que sigan pendientes.

El reparto concreto se acordará por tarea; no se asignan responsabilidades fijas
en este documento. Podemos usar ramas cortas y cambios enfocados para facilitar
la revisión, conservando siempre una versión que compile.

## Documentación y alcance controlado

La documentación se escribe junto con cada mecanismo: propósito, mensajes/campos,
reglas, errores, parámetros, pruebas y fuentes técnicas aplicables. Los valores
ajustables se justifican con mediciones, no con optimizaciones prematuras.

Mantendremos un servidor y una interfaz sencilla. Añadiremos dependencias solo
cuando aporten una ventaja concreta y puedan funcionar localmente. No añadiremos
funciones ajenas al alcance aprobado para completar una etapa.

El README principal contiene el estado y los comandos vigentes; este archivo
contiene el plan; los documentos de cada etapa conservan decisiones y evidencias.
Así evitamos copiar instrucciones que después puedan quedar contradictorias.