# Evaluación offline opcional: JDK 21, Node, Chrome y Playwright previamente instalados.
$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    New-Item -ItemType Directory -Force build/classes | Out-Null
    $sources = Get-ChildItem src/main/java/prib/*.java | Select-Object -ExpandProperty FullName
    javac -encoding UTF-8 -d build/classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación' }
    node scripts/evaluate.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Falló la evaluación' }
} finally { Pop-Location }
