# Ejecuta pruebas funcionales con un heap pequeño para detectar cargas completas.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
& (Join-Path $PSScriptRoot 'build.ps1')
$classes = Join-Path $projectRoot 'build/classes'
$testClasses = Join-Path $projectRoot 'build/test-classes'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sources = @(Get-ChildItem (Join-Path $projectRoot 'src/test/java') -Recurse -Filter '*.java' | ForEach-Object FullName)
& javac --release 21 -encoding UTF-8 -cp $classes -d $testClasses @sources
if ($LASTEXITCODE -ne 0) { throw 'La compilación de pruebas falló.' }
& java -Xmx64m '-Djava.awt.headless=true' -cp "$classes;$testClasses" prib.StageOneTest
if ($LASTEXITCODE -ne 0) { throw 'Las pruebas fallaron.' }
