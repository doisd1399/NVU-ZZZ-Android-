@echo off
setlocal
cd /d "%~dp0"
node scripts\preflight.mjs --android
if errorlevel 1 (
  pause
  exit /b 1
)
call npx cap open android
if errorlevel 1 pause
