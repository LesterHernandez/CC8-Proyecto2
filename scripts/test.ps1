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
} finally {
    Pop-Location # Restaurar la ubicación de quien invocó el script.
}
