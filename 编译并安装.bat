@echo off
cd /d "%~dp0"
echo ============================================
echo  UnlockHub / SafePing build and install
echo ============================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-apk.ps1"
echo.
echo ============================================
echo  Done. You can close this window.
echo ============================================
pause
