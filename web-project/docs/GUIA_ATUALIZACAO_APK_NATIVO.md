# Guia oficial de atualização do APK nativo

**Projeto:** NVU Operacional  
**Diretório:** `/home/ubuntu/work/nvu-web24-local-ota`  
**Release nativa atual:** `R3.34-PC-HF208`  
**Application ID:** `com.nvu.operacional`  
**Version name:** `1.0.286`  
**Version code:** `286`  
**Canal OTA derivado:** `production-286`  
**Runtime Web:** `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR`

> Este guia é específico para este projeto. Ele não contém senha, alias secreto, caminho de keystore, `google-services.json`, chave privada OTA ou token.

## 1. Quando é necessário gerar novo APK?

Gere novo APK quando a mudança exigir código, configuração, recurso ou compatibilidade que não possa ser transportada por um ZIP Web. A base nativa atual contém Capacitor local, bridge GTO, serviços Android, permissões especiais, Firebase Authentication nativo, ML Kit e Live Update; todos esses elementos são compilados no APK.

Não gere APK apenas porque a versão Web mudou. Para uma alteração Web compatível, use o canal do APK instalado e incremente somente `webVersion`/`bundleId`, preservando a linha nativa.

## 2. Arquitetura nativa real

### 2.1 Capacitor local

`capacitor.config.ts` declara:

| Campo | Valor real |
| --- | --- |
| `appId` | `com.nvu.operacional` |
| `appName` | `nvu` |
| `webDir` | `dist` |
| `server.url` | Ausente; o Web é local |
| `autoUpdateStrategy` | `none` |
| `defaultChannel` | `production-286` |
| `readyTimeout` | `10.000 ms` quando OTA está habilitado |
| `autoBlockRolledBackBundles` | Ativo quando flag, URL e chave pública OTA estão presentes |

O APK começa com os arquivos Web incorporados em `android/app/src/main/assets/public`. O OTA self-hosted é uma atualização opt-in de background e não uma substituição do bootstrap local.

### 2.2 Identidade Android

`android/app/build.gradle` define:

| Campo | Valor atual |
| --- | --- |
| Namespace | `com.nvu.operacional` |
| `applicationId` | `com.nvu.operacional` |
| `versionCode` | `286` |
| `versionName` | `1.0.286` |
| Canal padrão | `production-` + `versionCode` |
| Min SDK | `24` |
| Compile SDK | `36` |
| Target SDK | `36` |
| Android Gradle Plugin | `8.13.0` |
| Gradle Wrapper | `8.14.3` |
| Google Services plugin | `4.4.4` |
| Capacitor Android | `8.4.2` |
| Firebase Authentication plugin | `8.5.1` |
| Live Update plugin | `8.4.2` |

`android/variables.gradle` fixa também AndroidX, Cordova Android `14.0.1`, Credential Manager `1.3.0` e `rgcfaIncludeGoogle = true` para o Google Sign-In nativo.

### 2.3 Plugins e superfície nativa

O APK usa `@capacitor-firebase/authentication`, `@capacitor/push-notifications`, `@capawesome/capacitor-live-update`, ML Kit Text Recognition `16.0.1`, Firebase Auth/Functions nativos e o projeto `capacitor-cordova-android-plugins`.

O `GtoObserverPlugin.java` é uma autoridade nativa para o observador GTO e contém uma constante de runtime esperado. Ela deve permanecer alinhada com `src/lib/gtoRuntimeRevision.ts`, `package.json.gtoWebRuntimeRevision`, `dist/nvu-build.json` e os assets locais do APK.

O `AndroidManifest.xml` declara superfícies que não podem ser enviadas por OTA:

| Recurso | Estado real |
| --- | --- |
| `INTERNET` | Declarado |
| `SYSTEM_ALERT_WINDOW` | Declarado para overlay especial |
| `PACKAGE_USAGE_STATS` | Declarado para Usage Access |
| `FOREGROUND_SERVICE` | Declarado |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Declarado |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Declarado |
| `POST_NOTIFICATIONS` | Declarado |
| `MainActivity` | Launcher, `singleTask` |
| `GtoProjectionPermissionActivity` | Activity nativa não exportada |
| `GtoObserverService` | Foreground service specialUse/mediaProjection |
| `FileProvider` | Provider nativo |

Alterar qualquer uma dessas superfícies exige APK.

## 3. `versionCode` versus `versionName`

`versionCode` é o número inteiro usado pelo Android para ordenar builds e estabelecer a linha do canal OTA. Neste projeto, o canal é derivado automaticamente como `production-<versionCode>`. Por isso, o APK atual `versionCode 286` usa `production-286`.

`versionName` é a identificação legível exibida ao usuário. Atualmente é `1.0.286`. Ele não substitui o `versionCode` e não deve ser usado para decidir compatibilidade OTA.

Quando houver novo runtime Android ou mudança nativa, incremente o `versionCode` e defina um `versionName` coerente. O novo canal será derivado do novo `versionCode`, por exemplo `production-287` somente se o APK realmente passar a ter `versionCode 287`.

Uma alteração Web-only não exige incremento de nenhum dos dois. O Web pode ir de `2.3.51` para `2.3.52` mantendo APK `1.0.286`/`286`, desde que o runtime permaneça compatível.

## 4. Fluxo oficial de release APK

Todos os comandos partem de `/home/ubuntu/work/nvu-web24-local-ota`.

### Etapa 1 — Confirmar que a mudança é realmente nativa

Antes de incrementar versão, classifique a alteração. Código Java/Kotlin, plugin Capacitor, permissões, Manifest, Gradle, SDK, recurso nativo, bridge ou runtime incompatível exige APK. React/TypeScript/CSS compatível deve permanecer Web/OTA.

### Etapa 2 — Atualizar a versão nativa

Edite `android/app/build.gradle` somente quando a mudança nativa estiver comprovada:

```gradle
versionCode <novo_inteiro_maior_que_286>
versionName "<nova-versao-legivel>"
```

O `resValue` do canal usa `production-` + `versionCode`; não edite o canal para um valor arbitrário.

Atualize também `NVU_RELEASE_METADATA.json` e qualquer contrato de runtime correspondente. Se o runtime Web nativo mudar, atualize de forma alinhada:

```text
package.json.gtoWebRuntimeRevision
src/lib/gtoRuntimeRevision.ts
GtoObserverPlugin.java EXPECTED_WEB_RUNTIME_REVISION
nvu-build.json da Dist
nvu-build.json dos assets Android
manifest OTA do novo canal
```

### Etapa 3 — Instalar dependências e validar Web

```bash
npm ci
npm run lint
npm run build
npm run verify:release
```

Mesmo em um release nativo, a camada Web que será incorporada deve passar os gates. O build exige arquivos OCR locais, `nvu-build.json`, runtime coerente e configuração OTA consistente.

**Pare** se qualquer gate falhar.

### Etapa 4 — Sincronizar assets Capacitor

O script específico do projeto copia a Dist para o fallback Web local do Android, remove uma árvore antiga inteira para evitar chunks obsoletos, preserva apenas bridges gerados e remove a cópia comprimida do modelo OCR que causaria colisão no aapt2:

```bash
npm run prepare:cap-assets
npm run verify:cap-assets
npm run verify:cap-local
```

Quando plugins ou configuração Capacitor também mudarem, sincronize o projeto Android:

```bash
npx cap sync android
```

Depois da sincronização, execute novamente `npm run prepare:cap-assets`, `npm run verify:cap-assets` e `npm run verify:cap-local` para garantir que os assets incorporados continuam sendo uma cópia determinística da Dist.

O gate local verifica ausência de `server.url`, `webDir: "dist"`, `index.html` incorporado, runtime igual entre package, código nativo, Dist e assets Android, e `capacitorRuntime: "local"` nos dois manifestos.

### Etapa 5 — Preparar signing

O `android/app/build.gradle` aceita estas propriedades/variáveis externas:

| Variável | Uso | Segredo |
| --- | --- | --- |
| `RELEASE_STORE_FILE` | arquivo keystore | Sim |
| `RELEASE_STORE_PASSWORD` | senha do keystore | Sim |
| `RELEASE_KEY_ALIAS` | alias de assinatura | Pode ser sensível |
| `RELEASE_KEY_PASSWORD` | senha da chave | Sim |

Se essas quatro informações não estiverem presentes, o release Gradle não deve ser considerado assinado. O `google-services.json` também é obrigatório para um release com login Google nativo; o próprio `build.gradle` interrompe o release quando ele está ausente.

Não coloque senha, alias, keystore ou `google-services.json` no repositório, ZIP, documentação ou upload Netlify.

### Etapa 6 — Consultar tarefas Gradle reais

O Wrapper do projeto é Gradle `8.14.3`. Confirme tarefas antes de executar:

```bash
cd android
./gradlew tasks --all --console=plain
```

As tarefas de app release auditadas são:

```bash
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
```

`assembleRelease` produz o APK release; `bundleRelease` produz o bundle Android quando o canal de distribuição exigir AAB. Execute somente o artefato exigido pelo processo de entrega, não ambos por hábito.

### Etapa 7 — Gerar o APK

Depois de Web, assets, signing e gates passarem:

```bash
cd android
./gradlew :app:assembleRelease
```

Confirme o arquivo produzido em `android/app/build/outputs/apk/release/` e registre tamanho, SHA-256 e nome do artefato. Não renomeie um APK de outra versão.

### Etapa 8 — Verificar assinatura e identidade

Confirme novamente:

```bash
cd android
./gradlew :app:tasks --all --console=plain | grep -i signing
```

Se `signingReport` estiver disponível no ambiente, execute:

```bash
./gradlew :app:signingReport
```

Para verificação criptográfica do arquivo, use `apksigner` do Android SDK somente se estiver instalado e localizado no ambiente; o caminho do SDK é externo e não foi fixado pelo projeto. Não invente o caminho. A verificação deve confirmar assinatura válida, certificado esperado, `versionCode 286` ou o novo valor deliberado e `versionName 1.0.286` ou o novo valor deliberado.

O resultado do gate Gradle não substitui teste de instalação. Preserve o APK original, o APK novo, logs, checksums e metadados.

### Etapa 9 — Instalar e testar

A instalação física/ADB não é automatizada por este projeto. Em um ambiente com ADB autorizado, confirme o dispositivo e instale o APK release pelo procedimento operacional aprovado. Nunca declare teste físico sem executar o teste.

Valide, nesta ordem:

1. instalação e abertura do APK;
2. bootstrap Web local sem depender de rede;
3. login Google nativo;
4. logout e login novamente;
5. seletor de perfil e abertura imediata;
6. perfil Admin e Motorista;
7. GTO, permissões e retorno do simulador;
8. OCR/print e registro de viagem;
9. OTA no canal correspondente;
10. fechamento/reabertura para ativar bundle staged;
11. rollback apenas em procedimento controlado.

### Etapa 10 — Publicar OTA do novo APK

Depois que o novo APK existir, o bundle Web deve apontar para o novo canal derivado do novo `versionCode`. Para o APK atual, esse canal é `production-286`; para um novo APK, derive o canal novamente e não reutilize `production-286` se o `versionCode` tiver mudado.

O bundle precisa usar o mesmo `runtimeRevision` esperado pelo APK. Se o runtime mudou, gere o APK e só depois publique bundles compatíveis.

## 5. Relação “novo APK + OTA”

Um novo APK estabelece uma nova base nativa. Ele pode alterar `versionCode`, canal, runtime esperado, permissões, plugins, bridge e política Live Update. O OTA é compatível somente quando todos os campos do manifesto correspondem à base nativa:

```text
APK versionCode 286
APK channel production-286
APK runtime R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR
             ↓
OTA manifest nativeVersionCode 286
OTA nativeChannel production-286
OTA runtimeRevision R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR
```

Para o APK atual, `production-286-2.3.52` é compatível. Um manifest com `nativeVersionCode 285`, canal `production-285`, runtime divergente ou assinatura de outra chave deve ser rejeitado. Não corrija essa divergência apenas editando o manifest; alinhe a base e o bundle ou pare o release.

## 6. Checklist de migração para novo computador

| Item | Verificação | Externo/sensível? |
| --- | --- | --- |
| Node | `node --version`; mínimo declarado `>=22.0.0` | Não |
| npm | `npm --version`; mínimo declarado `>=10.0.0` | Não |
| Dependências | `npm ci` | Não |
| Java/JDK | necessário ao Gradle/Android; versão exata não está declarada como campo único do projeto | **NÃO CONFIRMADO — requer ambiente externo** |
| Android SDK | compile/target SDK 36; localizar SDK no ambiente | Externo |
| Gradle | Wrapper `gradle-8.14.3-all.zip` | Não |
| Capacitor CLI | `npx cap --version` e `npx cap sync android` | Não |
| Android Studio | útil para inspeção/ADB, não exigido pelo script Gradle | Externo |
| Firebase | projeto `vtc-frota-log`; `google-services.json` para release Android | Configuração/credencial externa |
| Keystore | quatro variáveis `RELEASE_*` | **Segredo externo** |
| Chave OTA | pública no runtime; privada externa ao repositório | **Segredo privado externo** |
| Netlify | `netlify status`; site `stirring-pavlova-ca6808` | Acesso externo |
| ADB/dispositivo | somente se teste físico for exigido | Externo |

Se qualquer item externo não estiver disponível, escreva **NÃO CONFIRMADO — requer configuração/credencial externa** e não declare o APK release como pronto.

## 7. Troubleshooting específico

### Login Google nativo falha no release

**Sintoma:** o APK release não autentica ou o Gradle interrompe o release.  
**Causa provável:** `google-services.json` ausente, inválido ou não correspondente ao projeto.  
**Diagnóstico:** conferir o erro do `android/app/build.gradle`, a presença do arquivo fora dos artefatos publicados e o projeto Firebase `vtc-frota-log`.  
**Solução segura:** recuperar o arquivo pela conta/procedimento autorizado, não copiá-lo para documentação/Netlify e repetir o build release.

### Fallback Web local não corresponde à Dist

**Sintoma:** Web pública funciona, mas o APK abre versão antiga ou o gate local reprova.  
**Causa provável:** assets Android não foram limpos/sincronizados, ou chunks antigos ficaram na árvore embutida.  
**Diagnóstico:** executar `npm run verify:cap-local` e comparar os dois `nvu-build.json`.  
**Solução segura:** executar `npm run prepare:cap-assets`, `npx cap sync android` quando necessário, repetir o preparo e os gates.

### OTA rejeitada por runtime/canal/versionCode

**Sintoma:** diagnóstico `OTA_RUNTIME_MISMATCH`, `OTA_CHANNEL_MISMATCH` ou `OTA_NATIVE_VERSION_MISMATCH`.  
**Causa provável:** manifest produzido para outro APK, canal manual incorreto ou runtime divergente.  
**Diagnóstico:** comparar `android/app/build.gradle`, `capacitor.config.ts`, `package.json`, `dist/nvu-build.json` e `manifest.json`.  
**Solução segura:** corrigir o alinhamento no release apropriado. Gere novo APK se o runtime nativo realmente mudou; não contorne via manifest.

### Checksum ou assinatura inválida

**Sintoma:** `OTA_CHECKSUM_INVALID` ou `OTA_SIGNATURE_INVALID`.  
**Causa provável:** ZIP mudou depois da assinatura, chave errada ou upload incompleto.  
**Diagnóstico:** baixar novamente o ZIP, calcular SHA-256, verificar assinatura com a chave pública do APK e comparar com o manifest.  
**Solução segura:** gerar novo bundleId, recalcular checksum, assinar novamente e republicar somente após todos os gates. Não reutilize assinatura antiga para ZIP alterado.

### Netlify retorna `401 Unauthorized`

**Sintoma:** deploy ou leitura da identidade falha com HTTP 401.  
**Causa provável:** CLI/MCP não está autenticado, mesmo que o navegador tenha uma sessão.  
**Diagnóstico:** executar `netlify status`; não confundir login do navegador com token do CLI.  
**Solução segura:** autenticar o CLI pela conta autorizada, confirmar site ID e executar um único deploy não interativo. Não inserir token no código.

### Netlify retorna `503 usage_exceeded`

**Sintoma:** manifest e ZIP retornam 503 apesar do staging local existir.  
**Causa provável:** limite de uso do site/conta.  
**Diagnóstico:** comparar alias de produção e URL imutável, registrar corpo/status e não assumir que upload aceito é publicação funcional.  
**Solução segura:** aguardar/resolver o limite administrativo antes de testar APK; não gerar APK ou republicar repetidamente às cegas.

### Deploy concluído, mas conteúdo antigo é servido

**Sintoma:** URL imutável e alias divergem, ou `nvu-build.json` continua em versão anterior.  
**Causa provável:** Dist copiada dentro de `staging/dist` em vez de substituir a raiz, alias ainda apontando para deploy anterior ou cache/propagação.  
**Diagnóstico:** baixar diretamente `nvu-build.json`, manifest e ZIP pelo alias e pela URL imutável.  
**Solução segura:** reconstruir staging com a Dist na raiz, preservar `ota/`, publicar novamente uma vez e validar por download; não declarar sucesso apenas pelo log de upload.

### Upload trava em um asset

**Sintoma:** CLI fica parado em uma fração dos arquivos.  
**Causa provável:** instabilidade de upload/protocolo, não necessariamente conteúdo inválido.  
**Diagnóstico:** verificar processo/deploy e estado público; não executar dois uploads concorrentes.  
**Solução segura:** encerrar o processo travado com segurança, aguardar estado conhecido e repetir uma vez com site ID explícito. Se persistir, parar e reportar.

## 8. Regras de segurança do release

Não publique APK não testado, não troque chave de assinatura, não sobrescreva histórico, não reutilize `bundleId` para conteúdo diferente, não publique OTA incompatível, não aumente `versionCode` para uma alteração somente Web, não resolva problema Web alterando Android e não tente resolver incompatibilidade nativa apenas com OTA.

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

A matriz representa a arquitetura auditada: o OTA transporta apenas arquivos Web compatíveis; qualquer mudança compilada ou declarada na camada Android exige novo APK.

## Referências do projeto

[package.json](../package.json), [capacitor.config.ts](../capacitor.config.ts), [android/app/build.gradle](../android/app/build.gradle), [android/build.gradle](../android/build.gradle), [android/variables.gradle](../android/variables.gradle), [android/gradle/wrapper/gradle-wrapper.properties](../android/gradle/wrapper/gradle-wrapper.properties), [AndroidManifest.xml](../android/app/src/main/AndroidManifest.xml), [prepare-capacitor-assets.mjs](../scripts/prepare-capacitor-assets.mjs), [verify-capacitor-local.mjs](../scripts/verify-capacitor-local.mjs), [verify-ota-ready.mjs](../scripts/verify-ota-ready.mjs), [OTA_ARCHITECTURE.md](OTA_ARCHITECTURE.md), [NVU_RELEASE_METADATA.json](../NVU_RELEASE_METADATA.json).
