@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0assemble_test_apk.ps1"
if errorlevel 1 (
  echo.
  echo APK verification failed. No APK was published by this script.
  pause
  exit /b 1
)
echo.
echo The verified APK was created in this folder. Please use test notes only.
pause
