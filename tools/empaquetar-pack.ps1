# Arma el resource pack de McCorp en Windows y muestra su SHA-1.
#
#   Clic derecho > "Ejecutar con PowerShell", o en una consola:
#   powershell -ExecutionPolicy Bypass -File tools\empaquetar-pack.ps1
#
# Deja build\mccorp-pack.zip y build\mccorp-pack.zip.sha1. Solo hace falta
# si vas a subir el pack a otro lado (modo "url"); con el modo "propio" el
# plugin MinerCorp-Pack ya trae el pack adentro y no hay que hacer nada.

$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$pack = Join-Path $raiz "resourcepack"
$build = Join-Path $raiz "build"
$zip = Join-Path $build "mccorp-pack.zip"

New-Item -ItemType Directory -Force -Path $build | Out-Null
if (Test-Path $zip) { Remove-Item $zip }

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archivo = [System.IO.Compression.ZipFile]::Open($zip, "Create")
try {
    Get-ChildItem -Path $pack -Recurse -File |
        Where-Object { $_.Name -ne ".gitkeep" -and $_.Extension -ne ".md" } |
        Sort-Object FullName |
        ForEach-Object {
            # Minecraft necesita "/" dentro del zip, no "\"
            $nombre = $_.FullName.Substring($pack.Length + 1).Replace("\", "/")
            [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archivo, $_.FullName, $nombre) | Out-Null
        }
} finally {
    $archivo.Dispose()
}

$sha1 = (Get-FileHash -Algorithm SHA1 $zip).Hash.ToLower()
Set-Content -Path "$zip.sha1" -Value $sha1
Write-Host "Listo: $zip"
Write-Host "sha1:  $sha1"
