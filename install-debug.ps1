# SafePing: compile debug APK and install to a connected phone.
$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot

function Find-JavaHome {
    $candidates = @(
        $env:JAVA_HOME,
        "$env:LOCALAPPDATA\Programs\Android Studio\jbr",
        "${env:ProgramFiles}\Android\Android Studio\jbr",
        "${env:ProgramFiles(x86)}\Android\Android Studio\jbr"
    ) | Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\java.exe")) }
    if ($candidates.Count -gt 0) { return $candidates[0] }
    return $null
}

function Find-AndroidSdk {
    $localProps = Join-Path $ProjectRoot "local.properties"
    if (Test-Path $localProps) {
        $line = Get-Content $localProps | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($line) {
            $raw = $line.Split('=', 2)[1].Trim()
            $sdk = $raw -replace '\\:', ':' -replace '\\', '\'
            if (Test-Path $sdk) { return $sdk }
        }
    }

    $candidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        "$env:LOCALAPPDATA\Android\Sdk"
    ) | Where-Object { $_ -and (Test-Path $_) }
    if ($candidates.Count -gt 0) { return $candidates[0] }
    return $null
}

$javaHome = Find-JavaHome
if (-not $javaHome) {
    Write-Host ""
    Write-Host "[ERROR] Java not found. Install Android Studio first:" -ForegroundColor Red
    Write-Host "  https://developer.android.com/studio"
    Write-Host ""
    Write-Host "Then run:"
    Write-Host "  powershell -ExecutionPolicy Bypass -File .\install-debug.ps1"
    exit 1
}

$env:JAVA_HOME = $javaHome
$env:Path = (Join-Path $javaHome "bin") + ";" + $env:Path

$sdk = Find-AndroidSdk
if (-not $sdk) {
    Write-Host ""
    Write-Host "[ERROR] Android SDK not found. Open Android Studio once to install the SDK." -ForegroundColor Red
    Write-Host "Then set local.properties, for example:"
    Write-Host "  sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk"
    exit 1
}

$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Host ""
    Write-Host "[ERROR] adb not found. Install Android SDK Platform-Tools in SDK Manager." -ForegroundColor Red
    exit 1
}

$escapedSdk = $sdk -replace '\\', '\\'
$localPropsPath = Join-Path $ProjectRoot "local.properties"
Set-Content -Path $localPropsPath -Value ("sdk.dir=" + $escapedSdk) -Encoding ASCII

Write-Host "JAVA_HOME = $javaHome"
Write-Host "ANDROID_SDK = $sdk"
Write-Host ""
Write-Host "Building and installing..." -ForegroundColor Cyan

Set-Location $ProjectRoot
& .\gradlew.bat installDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Connected devices:" -ForegroundColor Cyan
& $adb devices

Write-Host ""
Write-Host "Done. APK: app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green
