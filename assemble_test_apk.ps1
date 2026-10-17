# Assemble the verified APK using built-in Windows PowerShell 5.1+.
$ErrorActionPreference = 'Stop'
$Name = 'XuaneyNote-0.1.0-pr-a-19532a929475-debug.apk'
$ExpectedSha = '9def70c30bed6953a02151ef217aa6935f9948094298c78fc186bd823038f170'
$ExpectedSigner = 'c8e2c35d3f7dc5f8867485baca46eb2a9f0efff6671b72f0b6357b7104c72ee4'
$Base = 'https://raw.githubusercontent.com/lihuihnu/XuaneyNote/39a0d83a3908c99446bb5b96a4d7b9eb261f4b96/'
$Target = Join-Path $PSScriptRoot $Name
$Temp = Join-Path $PSScriptRoot '.xuaney-apk-incomplete'
if ([IO.File]::Exists($Target) -or [IO.File]::Exists($Temp)) {
    throw 'Refusing to overwrite an existing APK or temporary file'
}
$Client = New-Object System.Net.WebClient
$Client.Headers.Add('User-Agent', 'XuaneyNote-verified-test-apk/1')
function Get-Bytes([string]$Relative) {
    $Local = Join-Path $PSScriptRoot ($Relative -replace '/', [IO.Path]::DirectorySeparatorChar)
    if ([IO.File]::Exists($Local)) { return [IO.File]::ReadAllBytes($Local) }
    return $Client.DownloadData($Base + $Relative)
}
function Get-Hash([byte[]]$Bytes) {
    $shaObj = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($shaObj.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $shaObj.Dispose() }
}
try {
    $Meta = [Text.Encoding]::UTF8.GetString((Get-Bytes 'manifest.json')) | ConvertFrom-Json
    if ($Meta.apk_sha256 -ne $ExpectedSha -or $Meta.apk_size -ne 11023697 -or
        $Meta.apk_filename -ne $Name -or $Meta.source_sha -ne '19532a929475e9ce5e9f7fb2589654fb67f101d1' -or
        $Meta.signer_certificate_sha256 -ne $ExpectedSigner -or $Meta.run_id -ne '38995733428' -or
        $Meta.application_id -ne 'io.github.lihuihnu.xuaneynote.debug' -or $Meta.parts.Count -ne 11) {
        throw 'Unrecognized APK manifest'
    }
    $File = [IO.File]::Open($Temp, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        for ($i = 0; $i -lt 11; $i++) {
            $Path = 'parts/apk.part-{0:D2}' -f $i
            $Part = $Meta.parts[$i]
            if ($Part.path -ne $Path) { throw 'Out-of-order part' }
            [byte[]] $Bytes = Get-Bytes $Path
            if ($Bytes.Length -ne $Part.size -or (Get-Hash $Bytes) -ne $Part.sha256) {
                throw "Corrupt or missing chunk $Path"
            }
            $File.Write($Bytes, 0, $Bytes.Length)
            Write-Output ('Verified part {0}/11' -f ($i + 1))
        }
    } finally { $File.Dispose() }
    $FileInfo = Get-Item -LiteralPath $Temp
    if ($FileInfo.Length -ne 11023697) { throw 'Unexpected reconstructed APK length' }
    $FinalHash = (Get-FileHash -LiteralPath $Temp -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($FinalHash -ne $ExpectedSha) { throw 'APK SHA-256 mismatch' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $Zip = [IO.Compression.ZipFile]::OpenRead($Temp)
    try {
        $EntryNames = @($Zip.Entries | ForEach-Object { $_.FullName })
        if ($EntryNames -notcontains 'AndroidManifest.xml' -or $EntryNames -notcontains 'classes.dex') {
            throw 'Reconstructed output is not a valid Android APK ZIP'
        }
    } finally { $Zip.Dispose() }
    [IO.File]::Move($Temp, $Target)
    Write-Output "Verified APK: $Target"
    Write-Output "SHA-256: $ExpectedSha"
    Write-Output "CI signer SHA-256: $ExpectedSigner"
    Write-Output 'This is a temporarily signed debug APK, for disposable test notes only.'
} finally {
    $Client.Dispose()
    if ([IO.File]::Exists($Temp)) { [IO.File]::Delete($Temp) }
}
