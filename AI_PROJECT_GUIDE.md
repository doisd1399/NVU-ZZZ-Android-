# NVU Android/Web — Guia de localização e manutenção

## Repositório oficial

O projeto Web e o projeto Android pertencem ao mesmo repositório:

- Repositório: <https://github.com/doisd1399/NVU-ZZZ-Android-.git>
- Branch de trabalho atual: `fix/pro-operation-card-refresh`
- Branch principal: `main`
- Commit-base anterior: `f6d40c4` (`fix: refresh pro operation card after trip ack`)

Sempre confirmar a branch e o commit antes de editar. Não usar cópias antigas, branches não solicitadas ou outro repositório.

## Localização dos projetos

O projeto Web fica na raiz do repositório. Os pontos principais são:

- `src/` — aplicação React, contextos, layouts e páginas;
- `public/` — arquivos públicos e assets auxiliares;
- `dist/` — build Web gerada; normalmente não é rastreada pelo Git;
- `package.json` — scripts e dependências;
- `vite.config.*` — configuração do bundler;
- `netlify.toml` — build e publicação Netlify.

O projeto Android completo fica em `android/`:

- `android/app/` — módulo principal do aplicativo;
- `android/app/src/main/` — código Java, manifestos e assets nativos;
- `android/app/build.gradle` — versão, dependências e assinatura release;
- `android/gradlew` — wrapper Gradle;
- `android/app/build/outputs/apk/release/` — APKs gerados localmente.

A integração Capacitor fica em:

- `capacitor.config.ts` — configuração do app;
- `capacitor.remote.json` — runtime remoto OTA/Netlify;
- `android/app/src/main/assets/public/` — cópia Web sincronizada para o Android.

## Versão de referência atual

Conferir em `android/app/build.gradle`:

```text
versionCode 359
versionName "1.0.359"
```

A build Web correspondente gera `dist/nvu-build.json`, que deve indicar `otaEnabled: true` e `nativeChannel: production-359`.

## Procedimento para futuras atualizações

1. Clonar ou atualizar o repositório:

   ```bash
   git clone https://github.com/doisd1399/NVU-ZZZ-Android-.git
   cd NVU-ZZZ-Android-
   git checkout fix/pro-operation-card-refresh
   git pull --ff-only origin fix/pro-operation-card-refresh
   ```

2. Confirmar a identidade do código:

   ```bash
   git branch --show-current
   git log -1 --oneline --decorate
   git status --short
   ```

3. Instalar dependências e gerar a Web build:

   ```bash
   npm install
   npm run build
   ```

4. Confirmar `dist/index.html` e `dist/nvu-build.json`.

5. Sincronizar a Web com o Android:

   ```bash
   npx cap sync android
   ```

6. Compilar o APK release:

   ```bash
   cd android
   ./gradlew assembleRelease --no-daemon
   ```

7. Para gerar um APK assinado, o keystore nunca deve ser commitado no Git. Configurar temporariamente as variáveis abaixo no ambiente local, usando o keystore privado fornecido pelo proprietário:

   ```bash
   export NVU_KEYSTORE_FILE=/caminho/privado/release-keystore.jks
   export NVU_KEYSTORE_PASSWORD='senha-do-keystore'
   export NVU_KEY_ALIAS='nvukey'
   export NVU_KEY_PASSWORD='senha-da-chave'
   ./gradlew assembleRelease --no-daemon
   ```

   O `android/app/build.gradle` aplica a assinatura somente quando essas variáveis existem. Sem elas, a saída é unsigned.

8. Validar assinatura e versão:

   ```bash
   $ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose android/app/build/outputs/apk/release/app-release.apk
   grep -n 'versionCode\|versionName' android/app/build.gradle
   ```

## Regras de segurança e backup

- Não adicionar keystores, senhas, tokens ou `local.properties` ao GitHub.
- O `.gitignore` já protege `*.jks`, `*.keystore`, `android/key.properties` e arquivos `.env`.
- Backups locais podem conter material sensível, mas devem ser entregues somente por arquivo protegido e não publicados no GitHub.
- Antes de qualquer push, revisar `git diff --cached` e `git status --short`.

## Testes mínimos antes do commit

```bash
git diff --check
npm run build
npx cap sync android
cd android && ./gradlew assembleRelease --no-daemon
```

Depois, voltar à raiz e confirmar:

```bash
git status --short
git diff --stat
git log -1 --oneline --decorate
```

## Commit e push

Usar uma mensagem descritiva e publicar na branch de trabalho:

```bash
git add AI_PROJECT_GUIDE.md BACKUP_MANIFEST.txt public/tesseract \
  android/app/build.gradle src/context/AppContext.tsx src/index.css \
  src/layouts/AdminLayout.tsx src/layouts/DriverLayout.tsx

git commit -m "fix: sync fleet removal and mobile profile menu parity"
git push origin fix/pro-operation-card-refresh
```

Após o push, informar à próxima IA:

- URL do repositório;
- branch usada;
- hash do novo commit;
- versão Android;
- se o APK foi assinado ou unsigned;
- se a `dist` foi sincronizada;
- quaisquer arquivos que permaneceram apenas locais por segurança.

## URLs rápidas

- Repositório: <https://github.com/doisd1399/NVU-ZZZ-Android->
- Branch: <https://github.com/doisd1399/NVU-ZZZ-Android-/tree/fix/pro-operation-card-refresh>
- Android: <https://github.com/doisd1399/NVU-ZZZ-Android-/tree/fix/pro-operation-card-refresh/android>
- OTA: <https://github.com/doisd1399/NVU-ZZZ-Android-/blob/fix/pro-operation-card-refresh/capacitor.remote.json>
