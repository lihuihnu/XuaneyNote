@echo off
setlocal
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0assemble_test_apk.ps1"
if errorlevel 1 (
  echo.
  echo APK verification failed. No installable APK was delivered.
  pause
  exit /b 1
)
echo.
echo Reconstructed and hash-verified APK is now in this folder.
echo Install on a disposable test profile only. Do NOT remove important existing notes.
pause
