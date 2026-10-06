# Etapas de desarrollo de PRIB

El proyecto se completó de forma incremental. Cada etapa incorporó funcionalidad
sobre la anterior y se verificó con pruebas acordes a su alcance.

| Etapa | Trabajo realizado | Resultado |
| --- | --- | --- |
| 1. Base técnica | Lectura PNG por filas, bloques RGB y SHA-256; comparación con ImageIO en imágenes pequeñas | Fidelidad de píxeles y detección de errores verificadas |
| 2. Preparación y almacenamiento | Pirámide de resolución, bloques RAW/zlib, índices y consultas por región | Acceso a imágenes grandes sin cargarlas completas en RAM |
| 3. Servidor y visor | Servidor Java asíncrono, HTTP/WebSocket, sesiones, FULL, zoom, desplazamiento y pantalla completa | Exploración de regiones visibles con verificación de integridad |
| 4. Créditos | Capacidad por cliente, concesiones acumulativas, pausa/reanudación y reconexión | Envío limitado por capacidad sin bloquear a otros clientes |
| 5. Reutilización y planificación | REUSE/REF, caché limitada, prioridades, generaciones y cancelación | Reutilización de RGB y descarte seguro de vistas obsoletas |
| 6. Diferencias y recuperación | Firmas de similitud, DELTA exacto, selección por ahorro y recuperación selectiva a FULL | Cuatro modos operativos y fallos recuperables verificados |
| 7. Evaluación | Preparación de 28/55 GB, navegación prolongada, cuatro clientes, recursos y métricas | Funcionamiento comprobado con imágenes de hasta 55 GB |
| 8. Integración y cierre | Guías prácticas, demostración reproducible y consolidación del estado final | Proyecto funcional, documentado y reproducible por el equipo |

La integración final también incorporó preparación desde ZIP, archivo local y URL,
actualización del catálogo sin reinicio, formatos adicionales acotados y métricas
por vista. También se incorporaron acceso por Radmin VPN, SHA-256 local para HTTP
por IP, panel de transferencia organizado y logs resumidos por sesión.

Para utilizarlo, consultar el [README](../README.md); para entender la implementación,
el [protocolo](protocolo.md). Las [pruebas](pruebas.md) distinguen resultados medidos
de límites programados, y la [demostración](demostracion.md) reúne los escenarios.
