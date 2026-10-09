# Builds the game and assembles a redistributable overlay package that can be
# dropped into a purchased FarSky folder from itch.io.
#
# The package carries the compiled game, LWJGL natives and launch scripts.
# Every original asset (textures/, sounds/, obj/) is stripped out of the jar
# so nothing copyrighted is ever redistributed - the game picks the assets up
# from the owner's own farsky.jar (or an extracted res/ folder) at runtime.
#
# Output:
#   dist/farsky-restoration/       unpacked package
#   dist/farsky-restoration.zip    the same package, zipped
param(
    [string]$OutDir = (Join-Path $PSScriptRoot "..\dist")
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
# Normalize away "..\" segments: entry names are cut with String.Substring,
# which needs $OutDir/$stage to have the same length as the real file paths.
$OutDir = [System.IO.Path]::GetFullPath($OutDir)

Push-Location $repo
try {
    # 1. Build the distributable layout.
    & ".\gradlew.bat" installDist --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }

    $installLib = Join-Path $repo "farsky-app\build\install\farsky-app\lib"
    $stage = Join-Path $OutDir "farsky-restoration"
    if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
    New-Item -ItemType Directory -Path (Join-Path $stage "lib") -Force | Out-Null

    # 2. Game + dependency jars, natives, launchers, readme.
    Copy-Item (Join-Path $installLib "*.jar") (Join-Path $stage "lib")
    Copy-Item (Join-Path $repo "native") (Join-Path $stage "native") -Recurse
    Copy-Item (Join-Path $PSScriptRoot "Play FarSky.bat") $stage
    Copy-Item (Join-Path $PSScriptRoot "Play FarSky (windowed).bat") $stage
    Copy-Item (Join-Path $PSScriptRoot "DISTRIBUTION-README.txt") $stage

    # 3. Strip original assets from the game jar.
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jar = Join-Path $stage "lib\farsky-app.jar"
    $assetDirs = @("textures/", "sounds/", "obj/", "res/")
    $zip = [System.IO.Compression.ZipFile]::Open($jar, [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        $bad = @($zip.Entries | Where-Object {
            $e = $_.FullName
            ($assetDirs | Where-Object { $e.StartsWith($_) -or $e -eq $_ }) -ne $null
        })
        foreach ($entry in $bad) { $entry.Delete() }
    } finally {
        $zip.Dispose()
    }
    Write-Host "Stripped $($bad.Count) original asset entries from farsky-app.jar"

    # 4. Verify nothing copyrighted is left behind.
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $leak = @($zip.Entries | Where-Object {
            $e = $_.FullName
            ($assetDirs | Where-Object { $e.StartsWith($_) -or $e -eq $_ }) -ne $null
        })
    } finally {
        $zip.Dispose()
    }
    if ($leak.Count -gt 0) {
        throw "Refusing to ship: $($leak.Count) asset entries still in the jar"
    }
    Write-Host "Verified: jar contains no original assets"

    # 5. Zip it up. Entries are added manually with forward slashes - the
    #    Windows-only CreateFromDirectory helper writes backslashes instead,
    #    which some unzip tools outside Windows mishandle.
    $zipPath = Join-Path $OutDir "farsky-restoration.zip"
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    $zip = [System.IO.Compression.ZipFile]::Open($zipPath, [System.IO.Compression.ZipArchiveMode]::Create)
    try {
        Get-ChildItem $stage -Recurse -File | ForEach-Object {
            $rel = $_.FullName.Substring($stage.Length + 1).Replace("\", "/")
            $entry = $zip.CreateEntry($rel)
            $entryStream = $entry.Open()
            $fileStream = [System.IO.File]::OpenRead($_.FullName)
            $fileStream.CopyTo($entryStream)
            $fileStream.Dispose()
            $entryStream.Dispose()
        }
    } finally {
        $zip.Dispose()
    }

    $size = [math]::Round((Get-Item $zipPath).Length / 1MB, 1)
    Write-Host "OK: $zipPath ($size MB)"
} finally {
    Pop-Location
}
