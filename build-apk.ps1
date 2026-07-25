# SafePing Android 编译并安装脚本
$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$apkPath = Join-Path $projectRoot "app\build\outputs\apk\debug\app-debug.apk"

if (-not $env:JAVA_HOME) {
    $jdk = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
    if (Test-Path $jdk) {
        $env:JAVA_HOME = $jdk
    }
}
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}

Push-Location $projectRoot
try {
    Write-Host "正在编译 SafePing..."
    & .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "编译失败" }

    $apk = Get-Item $apkPath
    Write-Host "编译完成: $($apk.FullName)"
    Write-Host "时间: $($apk.LastWriteTime)  大小: $([math]::Round($apk.Length / 1KB, 1)) KB"

    $adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
    if (Test-Path $adb) {
        Write-Host "正在安装到手机..."
        & $adb install -r $apkPath
    } else {
        Write-Host "未找到 adb。可手动安装:"
        Write-Host "adb install -r `"$apkPath`""
    }
} finally {
    Pop-Location
}
