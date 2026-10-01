# Prepara todos los niveles sin extraer la imagen completa. La salida debe ser nueva.
param(
    [string]$Zip = '',
    [string]$Entry = '000-001-800-5105.png',
    [string]$Output = 'data/imagen-782'
)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    New-Item -ItemType Directory -Force build/classes | Out-Null
    $sources = Get-ChildItem src/main/java/prib/*.java | Select-Object -ExpandProperty FullName
    javac -encoding UTF-8 -d build/classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación' }
    # Sin -Zip se utiliza el archivo de imágenes pequeñas del curso.
    if (-not $Zip) {
        $file = Get-ChildItem imagenes/*.zip | Where-Object Name -Like '*Peque*' | Select-Object -First 1
        if (-not $file) { throw 'No se encontró el ZIP de imágenes pequeñas' }
        $Zip = $file.FullName
    }
    java -Xmx256m -cp build/classes prib.PrepareImage $Zip $Entry $Output
    if ($LASTEXITCODE -ne 0) { throw 'Falló la preparación; una salida incompleta no puede consultarse' }
} finally { Pop-Location }
