# PRIB — Visor de imágenes de alta resolución

Proyecto CC8 finalizado y funcional. Un servidor Java
asíncrono entrega los bloques necesarios para explorar imágenes grandes. PRIB usa
FULL/REUSE/REF/DELTA, SHA-256, créditos, caché limitada, prioridades y recuperación.
Se comprobó navegación hasta **55 GB con cuatro clientes simultáneos**.

## Requisitos e inicio

En la PC servidora: **JDK 21** (`java` y `javac` en el PATH), PowerShell y el proyecto.
Para visualizar: navegador actualizado. No se necesita Maven ni Node.js para usarlo.
Todos los comandos se ejecutan desde la raíz del repositorio.

**Solo localhost:**

```powershell
./scripts/servidor.ps1
```

Abrir [http://localhost:8080](http://localhost:8080). Mantener la terminal abierta;
Ctrl+C detiene el servidor. Si no hay imágenes, usar **Preparar imagen** según la
[guía de preparación](docs/preparacion-equipo.md). Los ZIP y `data/` no vienen por Git.

**Compartir por Radmin VPN:**

```powershell
./scripts/servidor.ps1 -Red
```

Iniciar Radmin antes del servidor. Localhost sigue disponible; el otro equipo abre
`http://IP-RADMIN-DEL-SERVIDOR:8080`, sustituyendo el texto por la IP real.
Configurar **TCP 8080** en el Firewall según la
[guía de Radmin](docs/preparacion-equipo.md#uso-entre-dos-pcs-con-radmin-vpn).
El cliente remoto solo necesita Radmin y navegador.

Opciones: `-Port 8081` cambia el puerto y `-Data 'ruta/al/catalogo'` el directorio
de almacenes. Tras actualizar código, reiniciar y recargar con Ctrl+F5.
Si PowerShell bloquea el inicio, usar
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\servidor.ps1`
y añadir `-Red` si corresponde; el permiso solo afecta ese proceso.

## Uso

Elegir imagen, ampliar con rueda o +/− y arrastrar. **Ver imagen completa** vuelve
al encuadre general; X/Y solicita una región del nivel elegido. Nivel 0 conserva
el original y ampliar más del 100 % agranda píxeles, sin añadir detalle.

**Mostrar transferencia** separa vista y sesión. Se espera **Vista completa** y
**SHA-256 correcto**. La sesión abreviada permite seguir al cliente en los logs de
la terminal. **Reconectar** crea otra sesión y reinicia caché y créditos.

Todo el visor funciona sin Internet con herramientas y datos disponibles.
La demostración desconectada usa localhost y varias pestañas; descargar ZIP,
preparar una URL pública o establecer la VPN requiere la conectividad correspondiente.

## Organización y guías

| Ruta | Contenido |
| --- | --- |
| src/main/java/prib/ y src/test/java/prib/ | Implementación y pruebas Java |
| web/ y scripts/ | Visor, dependencias locales y comandos |
| imagenes/ y data/ | ZIP y almacenes preparados; excluidos de Git |
| build/ y output/ | Compilación, pruebas y exportaciones regenerables; excluidos de Git |
| docs/mediciones/ | Evidencias de ejecuciones identificadas |

- [Preparación del equipo](docs/preparacion-equipo.md): imágenes, formatos, espacio, Radmin y Firewall.
- [Protocolo](docs/protocolo.md): funcionamiento y límites de PRIB.
- [Pruebas](docs/pruebas.md): comandos, herramientas y resultados.
- [Demostración](docs/demostracion.md): recorrido manual y cuatro modos reproducibles.
- [Plan de desarrollo](docs/plan-desarrollo.md): las ocho etapas realizadas.
