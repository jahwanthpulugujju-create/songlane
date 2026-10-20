param(
    [Parameter(Mandatory=$true)][string]$KeyStore,
    [string]$Alias = 'songlane',
    [Parameter(Mandatory=$true)][string]$OutputApk,
    [string]$InputApk = (Join-Path $PSScriptRoot '../app/build/outputs/apk/release/app-release-unsigned.apk'),
    [string]$BuildToolsVersion = '36.0.0'
)
$ErrorActionPreference = 'Stop'
if (-not $env:ANDROID_HOME -or -not $env:JAVA_HOME) { throw 'Set ANDROID_HOME and JAVA_HOME (JDK 17).' }
if (-not $env:SONGLANE_SIGNING_PASSWORD) { throw 'Set SONGLANE_SIGNING_PASSWORD in this process. Never put the value in source or command arguments.' }
if (-not (Test-Path -LiteralPath $KeyStore)) { throw 'Signing keystore not found. Reuse the existing key for updates.' }
if (-not (Test-Path -LiteralPath $InputApk)) { throw 'Unsigned APK not found. Run gradlew.bat assembleRelease first.' }
$signerTask = Join-Path $env:ANDROID_HOME "build-tools/$BuildToolsVersion/apksigner.bat"
if (-not (Test-Path -LiteralPath $signerTask)) { throw 'Install the selected Android build tools.' }
& $signerTask sign --ks $KeyStore --ks-key-alias $Alias --ks-pass env:SONGLANE_SIGNING_PASSWORD --key-pass env:SONGLANE_SIGNING_PASSWORD --v4-signing-enabled false --out $OutputApk $InputApk
if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
& $signerTask verify --verbose --print-certs $OutputApk
if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
$hashTask = (Get-FileHash -LiteralPath $OutputApk -Algorithm SHA256).Hash.ToLowerInvariant()
$checksumTask = "$hashTask  $([IO.Path]::GetFileName($OutputApk))`n"
[IO.File]::WriteAllText("$OutputApk.sha256", $checksumTask, [Text.UTF8Encoding]::new($false))
Write-Output "Signed APK: $OutputApk"
