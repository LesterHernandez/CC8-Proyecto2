# Inicia el servidor en localhost. Mantener esta terminal abierta; Ctrl+C lo detiene.
param([int]$Port = 8080, [string]$Data = 'data')
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    New-Item -ItemType Directory -Force build/classes | Out-Null
    $sources = Get-ChildItem src/main/java/prib/*.java | Select-Object -ExpandProperty FullName
    javac -encoding UTF-8 -d build/classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación' }
    # La preparación se hace antes con preparar.ps1; el servidor solo consulta los almacenes.
    java -Xmx256m -cp build/classes prib.PribServer $Port $Data
    if ($LASTEXITCODE -ne 0) { throw 'El servidor terminó con error' }
} finally { Pop-Location }
