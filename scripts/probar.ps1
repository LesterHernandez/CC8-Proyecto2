# Compila y ejecuta la prueba. Cambiar Output permite conservar ejecuciones anteriores.
param(
    [string]$Entry = '000-001-800-5105.png',
    [string]$Output = 'output/prueba-782'
)
$ErrorActionPreference = 'Stop'
# Resolver rutas desde el proyecto aunque se invoque el script desde otra carpeta.
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    # javac compila las dos clases; las clases generadas no se incluyen en Git.
    New-Item -ItemType Directory -Force build/classes | Out-Null
    $sources = Get-ChildItem src/main/java/prib/*.java | Select-Object -ExpandProperty FullName
    javac -encoding UTF-8 -d build/classes $sources
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación' }

    # El nombre parcial evita depender de cómo se escribió la ñ del archivo descargado.
    $zip = Get-ChildItem imagenes/*.zip | Where-Object Name -Like '*Peque*' | Select-Object -First 1
    if (-not $zip) { throw 'Falta el ZIP de imágenes pequeñas en imagenes/' }

    # La prueba abre el ZIP directamente. 256 MiB limita el heap de Java, no toda su RAM.
    java -Xmx256m -cp build/classes prib.Probe $zip.FullName $Entry $Output
    if ($LASTEXITCODE -ne 0) { throw 'Falló la prueba' }
} finally {
    Pop-Location # Devolver la terminal a la carpeta original, incluso ante un error.
}
