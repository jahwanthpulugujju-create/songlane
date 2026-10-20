param([string]$DeviceSerial)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbPath = Join-Path $sdkRoot 'platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adbPath)) { throw 'Install Android SDK platform-tools, or set ANDROID_HOME.' }
if (-not $env:JAVA_HOME) {
    $jdkRoot = Join-Path $env:USERPROFILE '.gradle\jdks'
    $candidate = Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe') } | Select-Object -First 1
    if ($candidate) { $env:JAVA_HOME = $candidate.FullName }
}
if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Install JDK 17+ and set JAVA_HOME.' }
$env:ANDROID_HOME = $sdkRoot
$deviceLines = & $adbPath devices
$devices = @($deviceLines | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($DeviceSerial) {
    if ($DeviceSerial -notin $devices) { throw "Device $DeviceSerial is not connected/authorized." }
} elseif ($devices.Count -eq 1) { $DeviceSerial = $devices[0] }
else { throw 'Connect one authorized Android device/emulator, or pass -DeviceSerial.' }
Push-Location -LiteralPath $projectRoot
try {
    & '.\gradlew.bat' ':app:assembleDebug'
    if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
    & $adbPath -s $DeviceSerial install -r (Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk')
    if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
    & $adbPath -s $DeviceSerial shell am start -n 'app.songlane/.MainActivity'
    if ($LASTEXITCODE -ne 0) { throw 'App launch failed.' }
} finally { Pop-Location }
