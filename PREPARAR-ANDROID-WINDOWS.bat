@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
title NVU - Preparacao Android

echo ============================================================
echo NVU - INSTALACAO, BUILD E SINCRONIZACAO ANDROID
echo ============================================================
echo.

where node >nul 2>nul
if errorlevel 1 (
  echo [ERRO] Node.js nao encontrado. Instale o Node.js 22 LTS.
  pause
  exit /b 1
)

node scripts\preflight.mjs --install
if errorlevel 1 goto :erro

if not exist "android\app\google-services.json" (
  echo [ERRO] Falta android\app\google-services.json
  echo Copie o arquivo Firebase correto antes de continuar.
  pause
  exit /b 1
)

call npm config set registry https://registry.npmjs.org/ --location=project
if errorlevel 1 goto :erro

if exist node_modules (
  echo Removendo instalacao incompleta anterior...
  rmdir /s /q node_modules
  if exist node_modules (
    echo [ERRO] O Windows bloqueou a pasta node_modules.
    echo Feche Android Studio, VS Code, terminais e antivirus temporariamente e tente novamente.
    pause
    exit /b 1
  )
)

echo.
echo Instalando dependencias...
call npm ci --no-audit --no-fund
if errorlevel 1 goto :erro

echo.
echo Validando TypeScript...
call npm run lint
if errorlevel 1 goto :erro

echo.
echo Instalando e validando Firebase Functions...
call npm --prefix functions ci --no-audit --no-fund
if errorlevel 1 goto :erro
call npm --prefix functions run build
if errorlevel 1 goto :erro

echo.
echo Preparando auditoria portavel do Android...
set "NVU_LOCAL_PROPERTIES_BACKUP="
if exist "android\local.properties" (
  set "NVU_LOCAL_PROPERTIES_BACKUP=%TEMP%\nvu-local-%RANDOM%-%RANDOM%.properties"
  copy /y "android\local.properties" "!NVU_LOCAL_PROPERTIES_BACKUP!" >nul
  if errorlevel 1 goto :erro
  del /q "android\local.properties"
  if exist "android\local.properties" goto :erro
)

echo.
echo Executando certificacao funcional R3.34-PC-HF45 (base R3.34)...
call npm run verify:release
if errorlevel 1 goto :erro

echo.
echo Gerando build de producao...
call npm run build
if errorlevel 1 goto :erro

echo.
echo Preparando fallback web embarcado...
call npm run prepare:cap-assets
if errorlevel 1 goto :erro

echo.
echo Sincronizando Capacitor com Android...
call npx cap sync android
if errorlevel 1 goto :erro

echo.
echo Verificando paridade do Web R3.34-PC-HF45 com os assets Android...
call npm run verify:cap-assets
if errorlevel 1 goto :erro

echo.
echo Configurando Android SDK local para o build Gradle...
if defined NVU_LOCAL_PROPERTIES_BACKUP (
  if exist "!NVU_LOCAL_PROPERTIES_BACKUP!" (
    copy /y "!NVU_LOCAL_PROPERTIES_BACKUP!" "android\local.properties" >nul
    del /q "!NVU_LOCAL_PROPERTIES_BACKUP!" >nul 2>nul
    set "NVU_LOCAL_PROPERTIES_BACKUP="
    echo [OK] android\local.properties anterior restaurado.
  )
)
if not exist "android\local.properties" (
  if exist "%LOCALAPPDATA%\Android\Sdk" (
    >"android\local.properties" echo sdk.dir=%LOCALAPPDATA:\=/%/Android/Sdk
    echo [OK] Android SDK configurado em android\local.properties
  ) else (
    echo [ERRO] Android SDK nao encontrado no caminho padrao.
    echo Abra Android Studio ^> Tools ^> SDK Manager e confirme o Android SDK Location.
    goto :erro
  )
)

echo.
echo Validando compilacao Java real do Android com JDK 21...
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
  echo [ERRO] Este projeto exige Java 21 para validar a compilacao Android.
  echo Atualize o Android Studio ou configure JAVA_HOME para um JDK 21.
  goto :erro
)

echo Java em uso na validacao Android:
java -version
pushd android
call gradlew.bat :app:compileDebugJavaWithJavac --no-daemon
if errorlevel 1 (
  popd
  echo [ERRO] A compilacao Java real do Android falhou. Corrija o erro antes de publicar.
  goto :erro
)
popd
echo [OK] Compilacao Java real do Android validada.

echo.
echo ============================================================
echo [OK] PROJETO PREPARADO COM SUCESSO
echo R3.34-PC-HF45 corrige a fronteira critica Resultado -^> nova lista, impede status antigo de bloquear o proximo frete, isola a fila anterior da sessao atual e filtra overlays NVU da semantica OCR do GTO.
echo Regra preservada: lista de fretes certificada reaberta encerra o contexto anterior e inicia um novo ciclo de selecao.
echo HF45 altera somente o Android nativo em relacao a HF44. O Web/Netlify e Firebase Functions nao precisam de novo deploy apenas por esta hotfix.
echo Para producao, gere uma RELEASE assinada com a keystore oficial. Veja: COMANDOS-R3.34-HF45-RELEASE-WINDOWS.txt
echo ============================================================
pause
exit /b 0

:erro
if defined NVU_LOCAL_PROPERTIES_BACKUP (
  if exist "!NVU_LOCAL_PROPERTIES_BACKUP!" (
    copy /y "!NVU_LOCAL_PROPERTIES_BACKUP!" "android\local.properties" >nul 2>nul
    del /q "!NVU_LOCAL_PROPERTIES_BACKUP!" >nul 2>nul
  )
)
echo.
echo [ERRO] A preparacao foi interrompida. Leia a mensagem acima.
pause
exit /b 1
