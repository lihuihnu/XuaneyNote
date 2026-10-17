# Reconstruct the exact PR-B Android APK. Built-in Windows PowerShell 5.1+; no Python/network.
$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
$Name = 'XuaneyNote-0.2.0-pr-b-6614745c341b-debug.apk'
$ApkSha = '07e3c53bea21768f936431e514e91f4dd4eeed0fd8490df800ccc53ab0aa687c'
$CompressedSha = '16af9e9385acc5aec21a1a38cbd351b8e67a85f07bc054f147c895bc56824ee1'
$SignerSha = '23cc82196fa887bab92a2a09db11b41eeb0628dd0603d45ef48d5062920bc484'
$TempZip = Join-Path $Root '.xuaney-pr-b-zlib.partial'
$TempApk = Join-Path $Root '.xuaney-pr-b-apk.partial'
$Destination = Join-Path $Root $Name

function Hash-Bytes([byte[]]$Data) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha.ComputeHash($Data)).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
try {
    if ([IO.File]::Exists($Destination) -or [IO.File]::Exists($TempZip) -or [IO.File]::Exists($TempApk)) {
        throw 'Refusing to overwrite an existing APK or temporary file'
    }
    $Meta = Get-Content -LiteralPath (Join-Path $Root 'manifest.json') -Encoding UTF8 -Raw | ConvertFrom-Json
    if ($Meta.apk_filename -ne $Name -or $Meta.apk_sha256 -ne $ApkSha -or
        $Meta.compressed_sha256 -ne $CompressedSha -or $Meta.compressed_size -ne 10666489 -or
        $Meta.apk_size -ne 11187305 -or $Meta.run_id -ne '39035771479' -or
        $Meta.run_attempt -ne '1' -or $Meta.source_sha -ne '6614745c341bcd84019a40af2ab57d4ab7ee22b4' -or
        $Meta.tested_sha -ne '1896431055156954d8c1e2761ef02daa469fb41c' -or
        $Meta.signer_certificate_sha256 -ne $SignerSha -or
        $Meta.application_id -ne 'io.github.lihuihnu.xuaneynote.debug' -or
        $Meta.version_code -ne 2 -or $Meta.version_name -ne '0.2.0-pr-b-debug' -or
        $Meta.parts.Count -ne 11) {
        throw 'Unknown or unexpected signed APK metadata'
    }
    $Stream = [IO.File]::Open($TempZip, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        for ($i = 0; $i -lt 11; $i++) {
            $ExpectedPath = 'parts/apk.zlib.part-{0:D2}' -f $i
            $Part = $Meta.parts[$i]
            if ($Part.path -ne $ExpectedPath) { throw 'Unexpected part order' }
            $Path = Join-Path $Root ($ExpectedPath -replace '/', [IO.Path]::DirectorySeparatorChar)
            if (-not [IO.File]::Exists($Path)) { throw "Missing APK part: $ExpectedPath" }
            [byte[]] $Data = [IO.File]::ReadAllBytes($Path)
            if ($Data.Length -ne $Part.size -or (Hash-Bytes $Data) -ne $Part.sha256) {
                throw "Corrupted APK part: $ExpectedPath"
            }
            $Stream.Write($Data, 0, $Data.Length)
            Write-Host ('Verified zlib part {0}/11' -f ($i + 1))
        }
    } finally { $Stream.Dispose() }
    if ((Get-Item -LiteralPath $TempZip).Length -ne 10666489 -or
        (Get-FileHash -LiteralPath $TempZip -Algorithm SHA256).Hash.ToLowerInvariant() -ne $CompressedSha) {
        throw 'Combined compressed-data SHA-256 mismatch'
    }
    [byte[]] $Encoded = [IO.File]::ReadAllBytes($TempZip)
    $Header = ([int]$Encoded[0] * 256) + [int]$Encoded[1]
    if (($Encoded[0] -band 15) -ne 8 -or ($Header % 31) -ne 0 -or
        ($Encoded[1] -band 32) -ne 0 -or $Encoded.Length -lt 8) {
        throw 'Invalid zlib wrapper'
    }
    # PowerShell 5.1 supports RFC1951 DeflateStream, so skip zlib's 2-byte header and 4-byte Adler32.
    $InputStream = [IO.MemoryStream]::new($Encoded, 2, $Encoded.Length - 6, $false)
    $Deflate = [IO.Compression.DeflateStream]::new(
        $InputStream, [IO.Compression.CompressionMode]::Decompress)
    $Output = [IO.File]::Open($TempApk, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $Deflate.CopyTo($Output) }
    finally { $Output.Dispose(); $Deflate.Dispose(); $InputStream.Dispose() }
    if ((Get-Item -LiteralPath $TempApk).Length -ne 11187305 -or
        (Get-FileHash -LiteralPath $TempApk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $ApkSha) {
        throw 'Exact original signed APK bytes did not match'
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $Zip = [IO.Compression.ZipFile]::OpenRead($TempApk)
    try {
        $Names = @($Zip.Entries | ForEach-Object { $_.FullName })
        if ($Names -notcontains 'AndroidManifest.xml' -or $Names -notcontains 'classes.dex') {
            throw 'Reconstructed file is not a valid Android APK'
        }
    } finally { $Zip.Dispose() }
    [IO.File]::Move($TempApk, $Destination)
    Write-Host "Verified APK: $Destination"
    Write-Host "SHA-256: $ApkSha"
    Write-Host "CI debug certificate SHA-256: $SignerSha"
    Write-Host 'Temporary signing key: disposable test notes only; do not overwrite a differently signed installed app.'
} finally {
    if ([IO.File]::Exists($TempZip)) { [IO.File]::Delete($TempZip) }
    if ([IO.File]::Exists($TempApk)) { [IO.File]::Delete($TempApk) }
}
