# PRIB — Proyecto de Ciencias de la Computación VIII

Servidor asíncrono para imágenes grandes, desarrollado por etapas en Java 21.
**Estado actual: etapa 1 completada, base y prueba de lectura regional.** Todavía no hay
servidor HTTP/WebSocket, frontend ni transmisión PRIB.

## Plan y documentación

Para conocer el orden de trabajo y coordinar las tareas, comenzar por el
[plan de desarrollo](docs/plan-desarrollo.md). Contiene las ocho etapas, sus
criterios de aceptación, el estado actual y las pautas de colaboración.

| Documento | Contenido |
| --- | --- |
| Este README | Estado actual, requisitos y comandos para ejecutar el proyecto |
| [Plan de desarrollo](docs/plan-desarrollo.md) | Orden de implementación, tareas y criterios de cierre |
| [Decisiones de etapa 1](docs/decisiones-etapa-1.md) | Base técnica, transporte previsto y contrato de bloques |
| [Validación de etapa 1](docs/validacion-etapa-1.md) | Pruebas realizadas, resultados y límites comprobados |

El README se actualizará a medida que avance el proyecto; las decisiones y los
resultados específicos permanecerán en los documentos de cada etapa.

## Requisitos

- JDK 21 (`java` y `javac` disponibles en PATH).
- PowerShell. No se requieren Maven, Node ni descargas para esta etapa.
- `Imagenes-Pequeñas.zip` en la raíz para las pruebas con imágenes del curso.

## Compilar y probar

Ejecutar desde la raíz del proyecto:

```powershell
./scripts/build.ps1
./scripts/test.ps1
java -cp build/classes prib.Main help
```

Las pruebas usan un heap máximo de 64 MiB. Crean imágenes pequeñas temporales,
comprueban los píxeles de regiones y casos inválidos y eliminan sus propios archivos.
Un fallo detiene la ejecución con código de error.

## Probar las imágenes del curso

```powershell
# Extrae tres muestras pequeñas; no modifica el ZIP.
./scripts/prepare-images.ps1
java -cp build/classes prib.Main inspect data/originals/000-001-800-5105.png

# Lee una región de 256 x 256 con un heap máximo de 64 MiB.
java -Xmx64m -Djava.awt.headless=true -cp build/classes prib.Main region data/originals/000-001-800-5105.png 263 319 256 256 output/region-small.png

# Opcional: agrega el PNG de 4.06 GB; requiere ese espacio adicional en disco.
./scripts/prepare-images.ps1 -IncludeLarge
java -Xmx64m -Djava.awt.headless=true -cp build/classes prib.Main region data/originals/004-050-000-6650032.png 36487 36487 256 256 output/region-large.png
```

En PowerShell, si un argumento `-D` se interpreta de forma inesperada, escribirlo
entre comillas: `'-Djava.awt.headless=true'`.
La salida debe ser una ruta nueva: el programa evita sobrescribir archivos.
Para repetir una prueba, utilizar otro nombre de salida.

## Organización

```text
src/main/java/prib/        Entrada del programa
src/main/java/prib/image/  Lectura regional y representación RGB/hash
src/test/java/prib/        Comprobaciones funcionales
scripts/                  Compilación, pruebas y preparación de muestras
docs/                     Decisiones y resultados por etapa
data/originals/            Imágenes locales, excluidas de Git
output/                   Recortes de diagnóstico, excluidos de Git
build/                    Clases compiladas, excluidas de Git
```

## Alcance y siguiente paso

Se acepta el formato de las muestras: PNG RGB de 8 bits sin entrelazado.
Cada región está limitada a 512 x 512. La prueba no valida la integridad completa
del ZIP ni de todo el PNG; comprueba que la región solicitada se puede decodificar.

**Memoria acotada no significa acceso rápido:** llegar al final de un PNG requiere
recorrer el flujo comprimido anterior. No usaremos este lector por cada petición
del navegador. La etapa 2 preparará un almacén de bloques con acceso directo;
su procesamiento deberá evitar decodificar repetidamente la imagen por cada bloque.

Consultar [decisiones iniciales](docs/decisiones-etapa-1.md) y
[validación](docs/validacion-etapa-1.md). Todos los desarrollos deben mantener
comentarios explicativos en español, según la petición del usuario.
