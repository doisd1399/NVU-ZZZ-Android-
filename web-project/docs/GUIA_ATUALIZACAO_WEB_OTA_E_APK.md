# Guia oficial de atualização Web, OTA e APK

**Projeto:** NVU Operacional  
**Release funcional atual:** `R3.34-PC-HF208`  
**Fonte:** projeto local `/home/ubuntu/work/nvu-web24-local-ota`  
**Última versão Web auditada:** `2.3.52`  
**Última base nativa auditada:** Android `1.0.286`, `versionCode 286`  
**Canal OTA:** `production-286`

> Este documento descreve a arquitetura real encontrada no projeto. Ele não contém chaves privadas, senhas, tokens, keystores, `google-services.json` ou caminhos sensíveis de credenciais.

## 1. Resposta direta: alterei somente a Web. Preciso gerar outro APK?

**Não, desde que a alteração seja exclusivamente Web e permaneça compatível com o runtime nativo já instalado.** Neste projeto, uma alteração de React/TypeScript, CSS, rota Web, interface, lógica de dados Web ou autenticação Web compatível pode ser publicada como novo bundle OTA para o canal do APK existente.

A base instalada atual é:

| Camada | Valor real |
| --- | --- |
| APK nativo | Android `1.0.286` |
| `versionCode` | `286` |
| Canal nativo/OTA | `production-286` |
| Runtime Web nativo | `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR` |
| Web atual | `2.3.52` |
| Bundle OTA atual | `production-286-2.3.52` |
| Hospedagem Web/OTA | `https://stirring-pavlova-ca6808.netlify.app` |
| Projeto Firebase | `vtc-frota-log` |

A versão Web não substitui nem incrementa automaticamente o `versionCode` Android. `2.3.52` e `1.0.286` são identificadores de camadas diferentes.

## 2. Arquitetura atual

### 2.1 Web, Dist e Netlify

O código Web vive na raiz do projeto e é compilado pelo Vite para `dist/`. O script `npm run build` executa o build e depois escreve `dist/nvu-build.json` através de `scripts/write-build-manifest.mjs`. Esse arquivo registra `version`, `buildId`, `runtimeRevision`, `capacitorRuntime`, `otaEnabled` e `otaManifestUrl`.

A raiz Web publicada no Netlify serve o site normal. O mesmo site também serve a árvore OTA estática:

```text
https://stirring-pavlova-ca6808.netlify.app/
└── ota/
    ├── production-278/
    ├── production-280/
    ├── production-281/
    ├── production-282/
    ├── production-283/
    ├── production-284/
    ├── production-285/
    └── production-286/
        ├── manifest.json
        └── nvu-live-update-2.3.52.zip
```

A Dist contém o Web local, inclusive os ativos OCR necessários. `scripts/verify-web-release.mjs` reprova a Dist quando faltam `nvu-build.json`, arquivos Tesseract/OCR, runtime coerente, versão coerente ou configuração OTA compilada.

### 2.2 Capacitor e APK

O `capacitor.config.ts` declara `webDir: "dist"` e não configura `server.url`. Portanto, o APK tem fallback Web local incorporado. O APK não depende de uma página remota para iniciar; o OTA self-hosted é opt-in e executado em background pelo cliente Live Update.

O app usa estes plugins relevantes:

| Plugin/camada | Função real |
| --- | --- |
| `@capacitor/android` | Runtime Android Capacitor |
| `@capacitor-firebase/authentication` | Autenticação Google/Firebase nativa |
| `@capawesome/capacitor-live-update` `8.4.2` | Download, verificação, staging e ativação de bundles |
| `@capacitor/push-notifications` | Notificações nativas |
| ML Kit Text Recognition `16.0.1` | OCR nativo Android |
| `GtoObserverPlugin` | Ponte nativa do observador GTO e permissões especiais |

### 2.3 OTA e fonte de verdade

`src/lib/otaManager.ts` é a única autoridade de execução do OTA. `src/lib/liveUpdate.ts` é somente uma fachada de compatibilidade. `LiveUpdateStatus.tsx` exibe estado, mas não inicia verificações. `deployRecovery.ts` não executa OTA.

O fluxo real é:

```text
startup/resume/intervalo
        ↓
OtaManager.check()
        ↓
manifest HTTPS
        ↓
origem + canal + nativeVersionCode + runtimeRevision
        ↓
checksum + assinatura
        ↓
downloadBundle()
        ↓
verifying
        ↓
setNextBundle()
        ↓
staged/completed
        ↓
fechar e reabrir o APK
        ↓
LiveUpdate.ready()
        ↓
bundle novo ativo ou rollback nativo
```

O OTA nunca força reload, não limpa sessão e não troca o bundle durante uma viagem. `setNextBundle()` prepara a próxima inicialização.

### 2.4 Contrato do manifesto

O manifesto atual precisa conter, no mínimo:

| Campo | Valor/Regra real |
| --- | --- |
| `artifactType` | `zip` |
| `bundleId` | `production-286-2.3.52` |
| `webVersion` | `2.3.52` |
| `nativeVersionCode` | `286` |
| `nativeChannel` | `production-286` |
| `runtimeRevision` | `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR` |
| `downloadUrl` | HTTPS, mesma origem do manifesto |
| `checksum` | SHA-256 hexadecimal de 64 caracteres |
| `signature` | assinatura RSA-SHA256 em Base64 |
| `signatureRequired` | `true` |

O cliente verifica origem HTTPS, mesma origem, formato do manifesto, canal, `nativeVersionCode`, runtime, checksum e assinatura antes de solicitar o staging ao plugin.

## 3. Regra fundamental de versionamento

### 3.1 Quando não é necessário gerar APK

Não gere APK quando todos os pontos abaixo forem verdadeiros:

1. A alteração está somente em Web/React/TypeScript/CSS ou lógica Web compatível.
2. `nativeVersionCode` continua `286`.
3. `nativeVersionName` continua `1.0.286`.
4. O canal continua `production-286`.
5. `runtimeRevision` continua `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR`.
6. O novo bundle passa checksum, assinatura e validação de compatibilidade.
7. O Web não exige plugin, permissão, serviço, bridge ou recurso nativo novo.

A sequência permitida para o mesmo APK é:

```text
APK Android 1.0.286 / versionCode 286
        ↓
OTA production-286-2.3.51
        ↓
OTA production-286-2.3.52
        ↓
OTA production-286-2.3.53
        ↓
...
```

Cada bundle deve ter `bundleId` próprio. Não reutilize o mesmo `bundleId` para conteúdo diferente.

### 3.2 Quando é obrigatório gerar novo APK

É obrigatório gerar novo APK quando houver mudança que o ZIP Web não consegue transportar ou que altere a compatibilidade do runtime, incluindo:

| Alteração | Novo APK? |
| --- | --- |
| Código Java/Kotlin do Android | Sim |
| `GtoObserverPlugin` ou bridge Capacitor | Sim |
| Plugin nativo novo, removido ou atualizado | Sim |
| `AndroidManifest.xml` | Sim |
| Permissões Android, activities, services ou providers | Sim |
| Serviço foreground, MediaProjection, overlay ou Usage Access | Sim |
| `android/app/build.gradle` nativo | Sim |
| SDK, `compileSdk`, `targetSdk` ou `minSdk` | Sim |
| `versionCode` ou `versionName` nativos | Sim |
| Configuração nativa que altere o runtime esperado | Sim |
| `runtimeRevision` incompatível com o APK instalado | Sim |
| Mudança que exige novo recurso dentro de `android/app/src/main` | Sim |
| React/TypeScript/CSS Web compatível | Não; OTA |
| Regra Web/Firebase JS compatível | Não; OTA, sem confundir com Rules/Functions |
| Rota ou tela Web compatível | Não; OTA |

Não tente corrigir incompatibilidade nativa apenas publicando OTA. Quando o runtime esperado pelo APK não coincide, interrompa o release e gere novo APK somente depois de atualizar e validar a base nativa.

## 4. Fluxo oficial Web/OTA

O procedimento abaixo é o fluxo operacional auditado. Todos os comandos devem ser executados a partir de `/home/ubuntu/work/nvu-web24-local-ota`.

### Etapa 1 — Alterar somente o Web

Altere os arquivos Web necessários. Não altere Android, Capacitor, Gradle, Manifest, `versionCode`, `versionName` ou plugins nativos para resolver uma demanda Web.

**Gate:** confirmar que o diff não contém alteração nativa indevida. **Pare** se a mudança tocar runtime nativo.

### Etapa 2 — Instalar e validar dependências

```bash
cd /home/ubuntu/work/nvu-web24-local-ota
npm ci
npm run lint
```

O projeto declara Node `>=22.0.0` e npm `>=10.0.0`. O `npm ci` usa o `package-lock.json`.

**Resultado esperado:** TypeScript sem erros. **Pare** se lint falhar.

### Etapa 3 — Gerar a Dist Web

```bash
npm run build
cat dist/nvu-build.json
```

O build executa `vite build`, `scripts/write-build-manifest.mjs` e `scripts/verify-web-release.mjs`.

**Resultado esperado:** `version: 2.3.52` ou a nova versão Web deliberadamente escolhida; `runtimeRevision` igual ao contrato; `capacitorRuntime: "local"`; `otaEnabled: true`; URL HTTPS de manifesto. **Pare** se qualquer valor divergir.

### Etapa 4 — Executar o release gate

```bash
npm run verify:release
```

Esse comando inclui `verify:ota-ready`, contrato do OTA, regras de viagem/GTO, autenticação, Profile Index, sessão, status Live Update, build e outros gates do projeto.

**Resultado esperado:** todos os gates passam. **Pare** no primeiro `FAIL`; não publique parcialmente.

### Etapa 5 — Gerar o bundle OTA

O empacotador oficial lê a Dist atual, valida versão/runtime/runtime local, verifica `versionCode`, remove artefatos proibidos e gera o ZIP:

```bash
node scripts/package-live-update-bundle.mjs \
  --output /tmp/nvu-live-update-2.3.52-production-286.zip
```

Ou, para executar build e empacotamento padrão:

```bash
npm run ota:bundle
```

O bundle esperado é `production-286-2.3.52`. Não reutilize `production-286-2.3.51` com conteúdo novo.

**Gate:** `unzip -tq /tmp/nvu-live-update-2.3.52-production-286.zip` deve passar. O ZIP não pode conter Android, APK, `google-services.json`, `local.properties`, `.env`, keystore ou `server.cjs`.

### Etapa 6 — Preparar manifesto e staging cumulativo

A assinatura usa a chave privada OTA fora do repositório. Nunca coloque essa chave no site, APK, Web, ZIP ou documentação.

```bash
export NVU_OTA_PRIVATE_KEY_PATH="<CAMINHO_EXTERNO_DA_CHAVE_PRIVADA>"
node scripts/prepare-netlify-ota.mjs \
  --bundle /tmp/nvu-live-update-2.3.52-production-286.zip \
  --site-output /tmp/nvu-ota-production-286-2.3.52 \
  --manifest-url https://stirring-pavlova-ca6808.netlify.app/ota/production-286/manifest.json
```

O preparador deriva `production-286` do `versionCode` nativo, calcula checksum, assina o bundle, escreve `manifest.json`, copia a Dist para a raiz e inclui o ZIP em `ota/production-286/`.

**Importante:** o preparador recria `--site-output`. Antes de publicar, mescle nele os manifests/bundles históricos do último site cumulativo aprovado. Preserve `production-286-2.3.51` e os canais `278`, `280`, `281`, `282`, `283`, `284` e `285`. Não remova histórico para reduzir upload.

### Etapa 7 — Validar localmente

```bash
export NVU_OTA_PUBLIC_KEY_PATH="<CAMINHO_EXTERNO_DA_CHAVE_PUBLICA>"
node scripts/verify-netlify-ota.mjs \
  --site-dir /tmp/nvu-ota-production-286-2.3.52
```

O gate valida manifesto, checksum, assinatura RSA, ZIP, HTTPS, runtime, canal, `versionCode`, ausência de Android/segredos e presença de `nvu-build.json`.

**Pare** se `bundleId`, checksum, assinatura, runtime, canal ou versionCode divergirem.

### Etapa 8 — Publicar no Netlify

Confirme a conta e o site antes de publicar:

```bash
netlify status
```

O site real é `stirring-pavlova-ca6808` e o site ID auditado é `874693d5-17b9-4de4-962e-b07cecaa982a`. Publique apenas o staging validado:

```bash
netlify deploy \
  --prod \
  --site=874693d5-17b9-4de4-962e-b07cecaa982a \
  --dir=/tmp/nvu-ota-production-286-2.3.52 \
  --message="NVU OTA production-286 2.3.52"
```

**Atenção:** upload concluído não é validação concluída. Registre o deploy ID retornado e continue para a etapa remota.

### Etapa 9 — Validar o site público

O validador oficial usa o domínio do projeto, lê o manifesto, baixa o ZIP com retry/resume e verifica checksum/integridade/histórico:

```bash
node scripts/validate-public-ota.mjs
```

O resultado esperado inclui:

```text
PUBLIC OTA VALIDATION PASS
build=2.3.52
manifest=production-286-2.3.52
native=production-286/286
zipIntegrity=true
historical=278:200,280:200,281:200,282:200,283:200,284:200,285:200
```

Valide também manualmente o conteúdo remoto:

```text
https://stirring-pavlova-ca6808.netlify.app/nvu-build.json
https://stirring-pavlova-ca6808.netlify.app/ota/production-286/manifest.json
https://stirring-pavlova-ca6808.netlify.app/ota/production-286/nvu-live-update-2.3.52.zip
```

**Pare** se o alias de produção servir versão antiga, se o manifesto retornar 503/404, se o ZIP remoto tiver tamanho/hash diferente ou se qualquer histórico retornar status diferente de 200.

### Etapa 10 — Preservar evidências

Guarde o bundle, manifesto local, `SHA256SUMS.txt`, relatório `PUBLIC_VALIDATION.json`, site cumulativo e nota de release. Não guarde a chave privada nesses artefatos.

## 5. Rollback e atualização repetida

O manager pode verificar, baixar e preparar o bundle durante o uso, mas ativa o bundle somente no próximo fechamento/reabertura. O plugin nativo controla `currentBundle`, `nextBundle` e o bloqueio de bundles revertidos. `autoBlockRolledBackBundles: true` é habilitado quando a configuração OTA está completa.

Se uma OTA apresentar erro:

1. pare novas publicações no mesmo canal;
2. preserve o manifesto e o ZIP problemáticos;
3. obtenha `getOtaDiagnostic()` e o estado técnico persistido;
4. identifique se a falha foi manifesto, canal, runtime, checksum, assinatura, download, staging ou rollback;
5. publique uma nova versão Web com `bundleId` novo e a mesma base nativa, se o runtime continuar compatível;
6. valide remotamente antes de orientar o usuário a reabrir o APK.

Não sobrescreva silenciosamente um ZIP com o mesmo `bundleId`. Não remova o histórico do bundle problemático; ele é necessário para auditoria e rollback operacional.

## 6. Assumir o projeto em outro ambiente

### Checklist técnico

| Item | Onde verificar | Segredo? |
| --- | --- | --- |
| Node/npm | `node --version`, `npm --version`; `package.json.engines` | Não |
| Dependências | `npm ci`, `package-lock.json` | Não |
| Versão Web | `package.json.version`, `dist/nvu-build.json` | Não |
| Runtime | `package.json.gtoWebRuntimeRevision`, `src/lib/gtoRuntimeRevision.ts`, Gradle/plugin | Não |
| Canal/versionCode | `android/app/build.gradle`, `capacitor.config.ts` | Não |
| URL OTA | `package.json.otaManifestUrl`, `nvu-build.json` | Não |
| Firebase | `firebase.json`, configuração Web e projeto `vtc-frota-log` | Configuração pública/operacional; tokens não |
| Netlify | `netlify status`, site ID, domínio | Acesso é externo |
| Chave privada OTA | variável `NVU_OTA_PRIVATE_KEY_PATH` | **Sim; externa** |
| Chave pública OTA | `NVU_OTA_PUBLIC_KEY_PATH`/Capacitor | Pública, mas não deve ser trocada sem processo |
| Signing APK | `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` | **Sim; externos** |
| `google-services.json` | `android/app/google-services.json` para release | **Sensível; não publicar** |
| Gradle | `android/gradle/wrapper/gradle-wrapper.properties` | Não |
| Assets locais | `npm run prepare:cap-assets`, `npm run verify:cap-local` | Não |

Se alguma credencial ou chave não estiver disponível, marque **NÃO CONFIRMADO — requer configuração/credencial externa** e pare antes de assinar/publicar.

## 7. Segurança

A chave privada OTA deve existir somente em um armazenamento externo autorizado e ser referenciada por variável de ambiente ou caminho protegido. A senha/alias do keystore APK também deve ser fornecida por propriedades Gradle ou variáveis de ambiente; nunca escreva esses valores em `package.json`, TypeScript, `capacitor.config.ts`, Markdown, ZIP ou Netlify.

O Firebase Authentication/Firestore e o Netlify exigem acesso administrativo separado. O login de uma conta no navegador não deve ser confundido com autorização do CLI/MCP. Valide `netlify status` e pare em `401 Unauthorized`.

## 8. Checklist final Web/OTA

- [ ] Alteração é somente Web.
- [ ] Runtime nativo permanece compatível.
- [ ] `versionCode 286` pode permanecer.
- [ ] `versionName 1.0.286` pode permanecer.
- [ ] Canal é `production-286`.
- [ ] `runtimeRevision` é `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR`.
- [ ] Chave OTA correta está disponível fora do repositório.
- [ ] `npm ci`, lint, build e `verify:release` passaram.
- [ ] Dist contém OCR e `nvu-build.json` coerente.
- [ ] ZIP passou `unzip -tq`.
- [ ] SHA-256 do manifesto corresponde ao ZIP.
- [ ] Assinatura RSA valida com a chave pública do APK.
- [ ] Manifesto aponta para HTTPS same-origin.
- [ ] BundleId é novo e correto.
- [ ] Histórico 2.3.51 e canais anteriores estão preservados.
- [ ] Deploy foi validado externamente, não apenas enviado.
- [ ] APK não é necessário.

## Referências do projeto

[package.json](../package.json), [capacitor.config.ts](../capacitor.config.ts), [Android build.gradle](../android/app/build.gradle), [Android variables.gradle](../android/variables.gradle), [AndroidManifest.xml](../android/app/src/main/AndroidManifest.xml), [write-build-manifest.mjs](../scripts/write-build-manifest.mjs), [verify-web-release.mjs](../scripts/verify-web-release.mjs), [package-live-update-bundle.mjs](../scripts/package-live-update-bundle.mjs), [prepare-netlify-ota.mjs](../scripts/prepare-netlify-ota.mjs), [verify-netlify-ota.mjs](../scripts/verify-netlify-ota.mjs), [validate-public-ota.mjs](../scripts/validate-public-ota.mjs), [verify-capacitor-local.mjs](../scripts/verify-capacitor-local.mjs), [OTA_ARCHITECTURE.md](OTA_ARCHITECTURE.md), [NVU_RELEASE_METADATA.json](../NVU_RELEASE_METADATA.json).

## 9. Matriz final de decisão

| Tipo de alteração | Web rebuild | OTA | Novo APK |
| --- | ---: | ---: | ---: |
| UI Web | Sim | Sim | Não |
| Lógica React/TypeScript Web | Sim | Sim | Não |
| Firebase/Web logic compatível | Sim | Sim | Não |
| Correção de rota Web | Sim | Sim | Não |
| Alteração Capacitor | Sim | Depende | Sim |
| Plugin nativo | Sim | Não | Sim |
| `AndroidManifest.xml` | Não/depende | Não | Sim |
| Permissão Android | Não | Não | Sim |
| Gradle/SDK | Não | Não | Sim |
| `versionCode`/`versionName` nativos | Não | Não | Sim |
| Runtime nativo incompatível | Sim | Não | Sim |
| Recurso Android, Activity, Service ou Provider | Não | Não | Sim |

A matriz significa que o Web/OTA só transporta arquivos Web compatíveis com o runtime já instalado. Quando a alteração toca a camada compilada Android ou muda a compatibilidade do runtime, o fluxo correto é o guia de APK nativo.

## Referências do projeto

[package.json](../package.json), [capacitor.config.ts](../capacitor.config.ts), [android/app/build.gradle](../android/app/build.gradle), [android/variables.gradle](../android/variables.gradle), [android/app/src/main/AndroidManifest.xml](../android/app/src/main/AndroidManifest.xml), [write-build-manifest.mjs](../scripts/write-build-manifest.mjs), [verify-web-release.mjs](../scripts/verify-web-release.mjs), [package-live-update-bundle.mjs](../scripts/package-live-update-bundle.mjs), [prepare-netlify-ota.mjs](../scripts/prepare-netlify-ota.mjs), [verify-netlify-ota.mjs](../scripts/verify-netlify-ota.mjs), [validate-public-ota.mjs](../scripts/validate-public-ota.mjs), [verify-capacitor-local.mjs](../scripts/verify-capacitor-local.mjs), [OTA_ARCHITECTURE.md](OTA_ARCHITECTURE.md), [NVU_RELEASE_METADATA.json](../NVU_RELEASE_METADATA.json).
