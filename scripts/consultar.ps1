# Exporta una región PNG desde una imagen preparada; no necesita el ZIP original.
param(
    [string]$Store = 'data/imagen-782',
    [int]$Level = 0,
    [int]$X = 0,
    [int]$Y = 0,
    [int]$Width = 256,
    [int]$Height = 256,
    [string]$Output = 'output/region.png'
)
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    New-Item -ItemType Directory -Force build/classes | Out-Null
    $sources = Get-ChildItem src/main/java/prib/*.java | Select-Object -ExpandProperty FullName
    javac -encoding UTF-8 -d build/classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación' }
    # Nivel 0 conserva el detalle original; niveles superiores son vistas reducidas.
    java -Xmx256m -cp build/classes prib.ViewRegion $Store $Level $X $Y $Width $Height $Output
    if ($LASTEXITCODE -ne 0) { throw 'Falló la consulta de región' }
} finally { Pop-Location }
