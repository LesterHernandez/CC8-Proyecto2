# PRIB — Visor de imágenes de alta resolución

Proyecto de Redes (CC8): un servidor asíncrono en Java envía al navegador
los bloques necesarios para explorar una imagen, sin descargarla completa.

**Etapas 1 a 4 terminadas:** preparación de imágenes, almacenamiento por
bloques, visor con zoom, SHA-256 y control por créditos. Probado con la imagen de
17 GB y dos clientes. Reutilización y DELTA quedan para las
[siguientes etapas](docs/plan-desarrollo.md).

## 1. Requisitos

- **JDK 21**, con `java` y `javac` disponibles en la terminal.
- **PowerShell** y un navegador actualizado.
- Los dos ZIP del curso dentro de `imagenes/`, sin descomprimir. Esa carpeta no viene al clonar: la [guía del equipo](docs/preparacion-equipo.md) incluye el enlace de descarga, los nombres y dónde colocarlos.

No se necesitan Maven ni Node.js para ejecutar el visor. Con las herramientas
y las imágenes disponibles, funciona sin internet.

Abre PowerShell en la raíz del proyecto, donde está este README.
Todos los comandos siguientes se ejecutan desde allí.

## 2. Preparar una imagen — solo la primera vez

Para tener las mismas seis imágenes del equipo, seguir la
[guía de preparación compartida](docs/preparacion-equipo.md).

**Si ya tienes imágenes preparadas en `data/`, pasa al paso 3.**

Para comenzar con la imagen pequeña del curso:

```powershell
./scripts/preparar.ps1 -Output 'data/imagen-782'
```

El comando busca el ZIP pequeño y prepara `000-001-800-5105.png`.
`data/imagen-782` es la **carpeta de destino**, no el nombre de la imagen.
Puedes elegir otro nombre; debe ser una carpeta nueva dentro de `data/`.

Para preparar otra imagen:

```powershell
./scripts/preparar.ps1 -Zip 'imagenes/Imagenes-28G-17G.zip' -Entry '017-110-000-24650032.png' -Output 'data/imagen-17gb'
```

| Parámetro | Qué representa |
| --- | --- |
| `-Zip` | Ruta del ZIP que contiene la imagen |
| `-Entry` | Nombre exacto del PNG dentro del ZIP |
| `-Output` | Carpeta nueva donde guardar la imagen preparada |

La preparación de imágenes grandes puede tardar varios minutos. No hace falta
repetirla cada vez que se abre el visor.

## 3. Iniciar el visor

```powershell
./scripts/servidor.ps1
```

Mantén esa terminal abierta y entra a **[http://localhost:8080](http://localhost:8080)**.
Para detener el servidor, presiona **Ctrl+C** en la terminal.

Si el puerto está ocupado, usa `./scripts/servidor.ps1 -Port 8081` y abre
`http://localhost:8081`. El servidor funciona únicamente en el equipo local.

Después de preparar nuevas imágenes o actualizar el código, reinicia el
servidor. Si el navegador sigue mostrando la versión anterior, usa **Ctrl+F5**.

## 4. Explorar la imagen

| Control | Uso |
| --- | --- |
| Imagen preparada | Seleccionar qué imagen explorar |
| Rueda o botones **+ / −** | Acercar y alejar; la rueda toma como referencia el cursor |
| Arrastrar | Desplazarse por la imagen |
| Nivel de detalle | El nivel 0 conserva la resolución original; los demás son versiones reducidas |
| X / Y e **Ir a la región** | Saltar a unas coordenadas en píxeles del nivel seleccionado |
| **Ver imagen completa** | Volver a la vista general |
| **Mostrar transferencia** | Revisar bloques, bytes, integridad y sesión |
| **Pantalla completa** | Ampliar el visor; salir con Escape |
| **Reconectar** | Iniciar una sesión nueva |

El zoom puede superar el 100 % para ver los números más grandes: amplía los
píxeles originales sin añadir información. Solo se solicitan los bloques
que intersectan la región visible.

## 5. Comprobar que funciona

1. Selecciona una imagen, acerca a **400 %** y arrastra hacia otra zona.
2. Abre **Mostrar transferencia**. Al terminar deben aparecer **Vista completa**,
   los bloques recibidos y esperados coincidentes, y **SHA-256 correcto**.
3. Abre otra pestaña y explora una región distinta. Las sesiones deben ser
   diferentes y cada pestaña debe funcionar de forma independiente.
4. Prueba cambiar el tamaño de la ventana, entrar en pantalla completa
   y volver con **Ver imagen completa**.

Para ejecutar las pruebas automáticas:

```powershell
./scripts/test.ps1
```

Deben terminar sin errores y mostrar mensajes `PASS`. Comprueban lectura PNG,
almacenamiento y comunicación con dos clientes; no requieren los ZIP del curso.

### Probar los créditos de etapa 4

En **Mostrar transferencia**, pulsa **Pausar devoluciones** y cambia de región
con la imagen de 17 GB al 100 %. El envío debe detenerse en **Esperando crédito**.
Otra pestaña debe seguir funcionando. Pulsa **Reanudar devoluciones** para completar
la vista. **Reconectar** crea una sesión nueva y vuelve a solicitar la región.
[Reglas y prueba completa de etapa 4](docs/etapa-4.md).

## Carpetas y documentación

| Carpeta | Contenido |
| --- | --- |
| `src/` | Código Java y pruebas |
| `web/` | Interfaz del visor |
| `scripts/` | Comandos de preparación, ejecución y pruebas |
| `imagenes/` | ZIP originales |
| `data/` | Imágenes preparadas; conservarlas para no repetir la preparación |
| `output/` | Resultados de pruebas y regiones exportadas |
| `build/` | Compilación y pruebas temporales |
| `docs/` | Plan, detalles técnicos y evidencia |

Los ZIP, imágenes preparadas y resultados generados no se suben a GitHub.
Puedes limpiar los resultados de `output/` si no los necesitas; comprueba
antes que no hayas elegido esa carpeta para guardar algún almacén preparado.

- [Plan de ocho etapas](docs/plan-desarrollo.md).
- [Etapa 1: lectura y bloques verificables](docs/etapa-1.md).
- [Etapa 2: preparación y almacenamiento](docs/etapa-2.md).
- [Pruebas manuales de preparación y consulta](docs/pruebas-manuales.md).
- [Etapa 3: servidor, visor, protocolo y zoom](docs/etapa-3.md).

- [Etapa 4: créditos y recuperación de sesión](docs/etapa-4.md).

- [Pruebas opcionales de navegador](docs/pruebas-navegador.md): sesiones, zoom, visor y créditos.
