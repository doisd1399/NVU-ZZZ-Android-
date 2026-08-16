@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
title NVU - Gerar APK Debug

echo ============================================================
echo NVU - COMPILACAO DO APK DE TESTE
echo ============================================================
echo.

if not exist "android\gradlew.bat" (
  echo [ERRO] Projeto Android nao encontrado.
  pause
  exit /b 1
)

set "JAVA21_READY="
set "NVU_JAVA21_CHECK=%TEMP%\nvu-java21-%RANDOM%-%RANDOM%.txt"

rem Primeiro tenta o JBR do Android Studio sem usar FOR /F sobre caminho com espacos.
if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
  "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" -version >"!NVU_JAVA21_CHECK!" 2>&1
  findstr /r /c:"21\." "!NVU_JAVA21_CHECK!" >nul
  if not errorlevel 1 (
    set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    set "PATH=!JAVA_HOME!\bin;!PATH!"
    set "JAVA21_READY=1"
  )
)

rem Se necessario, tenta um JAVA_HOME ja configurado sem depender do PATH.
if not defined JAVA21_READY if defined JAVA_HOME if exist "!JAVA_HOME!\bin\java.exe" (
  "!JAVA_HOME!\bin\java.exe" -version >"!NVU_JAVA21_CHECK!" 2>&1
  findstr /r /c:"21\." "!NVU_JAVA21_CHECK!" >nul
  if not errorlevel 1 (
    set "PATH=!JAVA_HOME!\bin;!PATH!"
    set "JAVA21_READY=1"
  )
)

rem Por ultimo, aceita um Java 21 ja disponivel no PATH.
if not defined JAVA21_READY (
  where java >nul 2>nul
  if not errorlevel 1 (
    java -version >"!NVU_JAVA21_CHECK!" 2>&1
    findstr /r /c:"21\." "!NVU_JAVA21_CHECK!" >nul
    if not errorlevel 1 set "JAVA21_READY=1"
  )
)

del /q "!NVU_JAVA21_CHECK!" >nul 2>nul

if not defined JAVA21_READY (
  echo [ERRO] Este projeto exige Java 21, mas o Java 21 nao foi encontrado.
  echo Atualize o Android Studio ou configure JAVA_HOME para um JDK 21 e tente novamente.
  pause
  exit /b 1
)

echo Java em uso:
java -version

if not exist "android\local.properties" (
  if exist "%LOCALAPPDATA%\Android\Sdk" (
    >"android\local.properties" echo sdk.dir=%LOCALAPPDATA:\=/%/Android/Sdk
    echo [OK] Android SDK configurado automaticamente.
  ) else (
    echo [ERRO] Android SDK nao localizado no caminho padrao.
    echo Abra Android Studio ^> Tools ^> SDK Manager e configure android\local.properties.
    pause
    exit /b 1
  )
)

node scripts\preflight.mjs --android
if errorlevel 1 (
  pause
  exit /b 1
)

echo Verificando se o Web R3.34 foi compilado e sincronizado com o APK...
node scripts\verify-capacitor-assets.mjs
if errorlevel 1 (
  echo.
  echo [ERRO] Os assets Android nao correspondem ao build Web atual.
  echo Execute PREPARAR-ANDROID-WINDOWS.bat e tente novamente.
  pause
  exit /b 1
)

pushd android
call gradlew.bat assembleDebug --no-daemon
if errorlevel 1 (
  popd
  echo.
  echo [ERRO] O Gradle nao conseguiu gerar o APK. Leia o erro acima.
  echo Confirme que o Android SDK 36 esta instalado no Android Studio.
  pause
  exit /b 1
)
popd

echo.
echo ============================================================
echo [OK] APK GERADO:
echo android\app\build\outputs\apk\debug\app-debug.apk
echo ============================================================
pause
