# Compila con el JDK instalado; no descarga dependencias ni requiere Maven.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$classes = Join-Path $projectRoot 'build/classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$sources = @(Get-ChildItem (Join-Path $projectRoot 'src/main/java') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 21 -encoding UTF-8 -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'La compilacion falló.' }
Write-Host 'Compilacion completada.'
