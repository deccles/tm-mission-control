# Regenerates src/main/resources/tm-mission-control.ico from icon.png.
# Requires ImageMagick 7+ on PATH (`magick`).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$src = Join-Path $root "src/main/resources/icon.png"
$out = Join-Path $root "src/main/resources/tm-mission-control.ico"
if (-not (Test-Path $src)) { throw "Missing: $src" }
$magick = Get-Command magick -ErrorAction SilentlyContinue
if (-not $magick) { throw "ImageMagick 'magick' not found on PATH." }
# -alpha on keeps PNG transparency in the ICO (avoids white corners on shortcuts / shell)
& $magick.Source $src -background none -alpha on -define icon:auto-resize=256,128,96,64,48,32,16 $out
Write-Host "Wrote $out"
