param([switch]$IncludeLarge)

# Extraemos únicamente muestras elegidas; el ZIP original permanece intacto.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$zipPath = Join-Path $projectRoot 'Imagenes-Pequeñas.zip'
$destination = Join-Path $projectRoot 'data/originals'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$names = @('000-000-033-103.png', '000-000-147-509.png', '000-001-800-5105.png')
if ($IncludeLarge) { $names += '004-050-000-6650032.png' }
$archive = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
try {
    foreach ($name in $names) {
        # Los nombres provienen de esta lista cerrada, nunca de rutas arbitrarias del ZIP.
        $entry = $archive.GetEntry($name)
        if ($null -eq $entry) { throw "No se encontró $name en el ZIP." }
        $target = Join-Path $destination $name
        if (Test-Path -LiteralPath $target) {
            if ((Get-Item -LiteralPath $target).Length -ne $entry.Length) {
                throw "El archivo existente tiene tamaño inesperado: $target"
            }
            Write-Host "Ya existe: $name"
            continue
        }
        # El archivo parcial se publica solo al completar la extracción.
        $partial = "$target.partial"
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $partial, $false)
        Move-Item -LiteralPath $partial -Destination $target
        Write-Host "Extraído: $name ($($entry.Length) bytes)"
    }
} finally {
    $archive.Dispose()
}
