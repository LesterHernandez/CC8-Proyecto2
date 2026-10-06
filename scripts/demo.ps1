# Compilar y demostrar PRIB sin ZIP del curso ni almacenes grandes.
param([string]$Node = 'node')
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    New-Item -ItemType Directory -Force build/classes | Out-Null
    $sources = Get-ChildItem src/main/java/prib/*.java,src/test/java/prib/*.java | Select-Object -ExpandProperty FullName
    javac -encoding UTF-8 -d build/classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación' }
    & $Node scripts/demonstrate.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Falló la demostración' }
} finally { Pop-Location }
