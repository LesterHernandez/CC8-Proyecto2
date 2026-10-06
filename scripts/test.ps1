# Compilar y ejecutar las pruebas sintéticas con el mismo JDK 21 del proyecto.
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    New-Item -ItemType Directory -Force build/classes | Out-Null
    # Incluir el código principal y las pruebas; no requiere librerías de testing.
    $sources = Get-ChildItem src/main/java/prib/*.java,src/test/java/prib/*.java |
        Select-Object -ExpandProperty FullName
    javac -encoding UTF-8 -d build/classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación' }
    java -Xmx256m -cp build/classes prib.PngRowsTest
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas PNG' }
    # Comprobar índices, compresión, niveles y consultas de la etapa 2.
    java -Xmx256m -cp build/classes prib.ImageStoreTest
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas del almacén' }
    # Contabilidad de bytes y concesiones acumulativas de etapa 4.
    java -Xmx256m -cp build/classes prib.CreditWindowTest
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas de créditos' }
    java -Xmx256m -cp build/classes prib.ClientCacheTest
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas de caché y prioridades' }
    java -Xmx256m -cp build/classes prib.DeltaCodecTest
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas DELTA y firmas' }
    # Servidor temporal en un puerto libre y dos clientes WebSocket reales.
    java -Xmx256m -cp build/classes prib.PribServerTest
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas de red PRIB' }
    java -Xmx256m -cp build/classes prib.DeltaProtocolTest
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas DELTA/RECOVER por red' }
    java -Xmx256m -cp build/classes prib.ImagePreparationTest
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las pruebas de preparación web' }
} finally {
    Pop-Location # Restaurar la ubicación de quien invocó el script.
}
