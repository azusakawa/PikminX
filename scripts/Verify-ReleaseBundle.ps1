[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string] $ManifestPath,

    [string] $AaptPath,

    [string] $ApksignerPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-B0FileSha256 {
    param([Parameter(Mandatory = $true)][string] $Path)

    $sha = [System.Security.Cryptography.SHA256]::Create()
    $stream = $null
    try {
        $stream = [System.IO.File]::Open(
            $Path,
            [System.IO.FileMode]::Open,
            [System.IO.FileAccess]::Read,
            [System.IO.FileShare]::Read
        )
        return ([System.BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
    }
    finally {
        if ($null -ne $stream) {
            $stream.Dispose()
        }
        $sha.Dispose()
    }
}

function Get-B0TreeSha256 {
    param([Parameter(Mandatory = $true)][object[]] $Files)

    $canonical = (($Files | Sort-Object path | ForEach-Object {
        "{0}`t{1}`t{2}" -f $_.path, $_.bytes, $_.sha256
    }) -join "`n")
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($canonical)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
    }
}

function Resolve-B0BundlePath {
    param(
        [Parameter(Mandatory = $true)][string] $BundleRoot,
        [Parameter(Mandatory = $true)][string] $RelativePath
    )

    if ([System.IO.Path]::IsPathRooted($RelativePath)) {
        throw "Bundle path must be relative: $RelativePath"
    }

    $root = [System.IO.Path]::GetFullPath($BundleRoot).TrimEnd([char]'\')
    $candidate = [System.IO.Path]::GetFullPath((Join-Path $root $RelativePath))
    $prefix = $root + '\'
    if (-not $candidate.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Bundle path escapes its root: $RelativePath"
    }
    return $candidate
}

function Find-B0AndroidTool {
    param(
        [string] $ProvidedPath,
        [Parameter(Mandatory = $true)][string] $FileName,
        [string] $BuildToolsVersion
    )

    if ($ProvidedPath) {
        if (-not (Test-Path -LiteralPath $ProvidedPath -PathType Leaf)) {
            throw "Specified Android build tool is missing: $ProvidedPath"
        }
        return (Resolve-Path -LiteralPath $ProvidedPath).Path
    }

    $sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
    if (-not $sdkRoot) {
        throw "Set ANDROID_SDK_ROOT or ANDROID_HOME, or supply the explicit $FileName path."
    }

    if ($BuildToolsVersion) {
        $candidate = Join-Path $sdkRoot ("build-tools\\$BuildToolsVersion\\$FileName")
        if (Test-Path -LiteralPath $candidate -PathType Leaf) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    $candidates = Get-ChildItem -LiteralPath (Join-Path $sdkRoot 'build-tools') -Directory -ErrorAction Stop |
        Sort-Object Name -Descending |
        ForEach-Object { Join-Path $_.FullName $FileName } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Leaf }
    if (-not $candidates) {
        throw "Unable to locate $FileName under $sdkRoot\\build-tools."
    }
    return (Resolve-Path -LiteralPath $candidates[0]).Path
}

try {
    $manifestFullPath = (Resolve-Path -LiteralPath $ManifestPath -ErrorAction Stop).Path
    $bundleRoot = Split-Path -Parent $manifestFullPath
    $manifest = Get-Content -LiteralPath $manifestFullPath -Raw -Encoding UTF8 | ConvertFrom-Json

    if ($manifest.schema -ne 'pikminx-release-manifest/v1') {
        throw 'Unsupported or missing release manifest schema.'
    }
    if (-not $manifest.requiredFiles) {
        throw 'Release manifest has no requiredFiles list.'
    }

    foreach ($requiredFile in $manifest.requiredFiles) {
        $requiredPath = Resolve-B0BundlePath -BundleRoot $bundleRoot -RelativePath $requiredFile
        if (-not (Test-Path -LiteralPath $requiredPath)) {
            throw "Required release-bundle entry is missing: $requiredFile"
        }
    }

    $sourcePath = Resolve-B0BundlePath -BundleRoot $bundleRoot -RelativePath $manifest.source.path
    $inventoryPath = Resolve-B0BundlePath -BundleRoot $bundleRoot -RelativePath $manifest.source.inventoryPath
    if (-not (Test-Path -LiteralPath $sourcePath -PathType Container)) {
        throw "Source snapshot is missing: $($manifest.source.path)"
    }
    if (-not (Test-Path -LiteralPath $inventoryPath -PathType Leaf)) {
        throw "Source inventory is missing: $($manifest.source.inventoryPath)"
    }

    $inventory = Get-Content -LiteralPath $inventoryPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($inventory.schema -ne 'pikminx-source-input-inventory/v1') {
        throw 'Unsupported or missing source inventory schema.'
    }
    if (-not $inventory.files) {
        throw 'Source inventory has no files.'
    }

    $actualSourceFiles = [System.Collections.Generic.List[object]]::new()
    foreach ($entry in $inventory.files) {
        $sourceFile = Resolve-B0BundlePath -BundleRoot $sourcePath -RelativePath $entry.path
        if (-not (Test-Path -LiteralPath $sourceFile -PathType Leaf)) {
            throw "Source snapshot file is missing: $($entry.path)"
        }
        $item = Get-Item -LiteralPath $sourceFile
        $actualSha256 = Get-B0FileSha256 -Path $sourceFile
        if (($item.Length -ne [int64]$entry.bytes) -or ($actualSha256 -ne $entry.sha256.ToLowerInvariant())) {
            throw "Source snapshot mismatch: $($entry.path)"
        }
        $actualSourceFiles.Add([pscustomobject]@{
            path = $entry.path
            bytes = [int64]$item.Length
            sha256 = $actualSha256
        })
    }

    $actualTreeSha256 = Get-B0TreeSha256 -Files $actualSourceFiles.ToArray()
    if (($actualTreeSha256 -ne $inventory.treeSha256.ToLowerInvariant()) -or
        ($actualTreeSha256 -ne $manifest.source.treeSha256.ToLowerInvariant())) {
        throw 'Source snapshot tree SHA-256 mismatch.'
    }

    $apkPath = Resolve-B0BundlePath -BundleRoot $bundleRoot -RelativePath $manifest.apk.path
    if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
        throw "Release APK is missing: $($manifest.apk.path)"
    }
    $apkSha256 = Get-B0FileSha256 -Path $apkPath
    if ($apkSha256 -ne $manifest.apk.sha256.ToLowerInvariant()) {
        throw "Release APK SHA-256 mismatch."
    }

    $buildToolsVersion = $manifest.environment.androidBuildToolsVersion
    $aapt = Find-B0AndroidTool -ProvidedPath $AaptPath -FileName 'aapt.exe' -BuildToolsVersion $buildToolsVersion
    $apksigner = Find-B0AndroidTool -ProvidedPath $ApksignerPath -FileName 'apksigner.bat' -BuildToolsVersion $buildToolsVersion

    $aaptOutput = & $aapt dump badging $apkPath 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "aapt failed while reading the release APK: $aaptOutput"
    }
    $packageLine = @($aaptOutput | Where-Object { $_ -match "^package: name='" })[0]
    if (-not $packageLine) {
        throw 'aapt did not return a package line.'
    }
    $packageMatch = [regex]::Match($packageLine, "name='([^']+)' versionCode='([^']+)' versionName='([^']+)'")
    if (-not $packageMatch.Success) {
        throw "Unable to parse aapt package line: $packageLine"
    }
    if (($packageMatch.Groups[1].Value -ne $manifest.apk.packageName) -or
        ($packageMatch.Groups[2].Value -ne [string]$manifest.apk.versionCode) -or
        ($packageMatch.Groups[3].Value -ne $manifest.apk.versionName)) {
        throw 'Release APK package or version does not match the manifest.'
    }

    $signerOutput = & $apksigner verify --verbose --print-certs $apkPath 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "apksigner verification failed: $signerOutput"
    }
    $certificateMatch = [regex]::Match(($signerOutput -join "`n"), '(?im)^Signer #1 certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$')
    if (-not $certificateMatch.Success) {
        throw 'apksigner did not return signer certificate SHA-256.'
    }
    if ($certificateMatch.Groups[1].Value.ToLowerInvariant() -ne $manifest.apk.signerCertificateSha256.ToLowerInvariant()) {
        throw 'Release APK signer certificate SHA-256 does not match the manifest.'
    }
    foreach ($scheme in $manifest.apk.verifiedSignatureSchemes) {
        $schemeMatch = [regex]::Match(($signerOutput -join "`n"), "(?im)^Verified using $([regex]::Escape($scheme)) scheme .*:\s*true\s*$")
        if (-not $schemeMatch.Success) {
            throw "Release APK did not verify with required signature scheme $scheme."
        }
    }

    Write-Output ("PASS {0} source={1} apk={2}" -f $manifest.buildId, $actualTreeSha256, $apkSha256)
}
catch {
    Write-Error ("B0 release verification failed: {0}" -f $_.Exception.Message)
    exit 1
}
