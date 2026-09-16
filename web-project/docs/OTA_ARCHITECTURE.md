# Arquitetura OTA da NVU

## 1. Autoridade única

O `OtaManager`, em `src/lib/otaManager.ts`, é a única autoridade de execução do OTA self-hosted. A fachada `src/lib/liveUpdate.ts` existe apenas para compatibilidade com imports antigos e delega ao manager. O componente `LiveUpdateStatus` é somente apresentação; não inicia verificações. O `deployRecovery` trata exclusivamente recuperação de deploy/chunks da Web e não inicia OTA Android.

## 2. Fluxo operacional

```text
startup/resume/intervalo
        ↓
OtaManager.check()
        ↓
manifest HTTPS
        ↓
estrutura + origem + canal + nativeVersionCode + runtimeRevision
        ↓
currentBundle/nextBundle
        ↓
downloadBundle(checksum + assinatura)
        ↓
verifying
        ↓
setNextBundle()
        ↓
staged/completed
        ↓
fechamento e reabertura do app
        ↓
LiveUpdate.ready()
        ↓
novo bundle ativo ou rollback nativo
```

A interface inicia primeiro. O manager é iniciado após a montagem inicial do React e executa em background. Falhas de manifesto, rede, checksum, assinatura, runtime ou staging não bloqueiam a abertura do NVU.

## 3. Estados

O contrato de status usa `idle`, `checking`, `available`, `downloading`, `verifying`, `staged`, `completed`, `failed` e `rolled_back`. O estado é persistido em `localStorage` apenas com metadados técnicos mínimos, sobrevive ao fechamento do app e é disponibilizado ao indicador global por memória/evento.

O usuário vê somente mensagens compactas durante download/verificação/staging e a confirmação temporária `Atualização concluída ✓`. O OTA não força reload durante o uso; `setNextBundle()` prepara a aplicação para o próximo reinício.

## 4. Compatibilidade e fonte de verdade

O `runtimeRevision` é derivado de `package.json.gtoWebRuntimeRevision` durante o build Vite. O canal é derivado do `versionCode` nativo como `production-<nativeVersionCode>`. O pipeline deve manter Web, `nvu-build.json`, APK, bundle e manifesto coerentes. O `prepare-netlify-ota.mjs` deriva o canal e o bundle ID do versionCode/build e falha quando os artefatos obrigatórios não existem.

O manifesto exige `artifactType`, `bundleId`, `webVersion`, `nativeVersionCode`, `nativeChannel`, `runtimeRevision`, `downloadUrl`, SHA-256 e assinatura. O cliente valida HTTPS, mesma origem, canal, versão nativa, runtime e formato do checksum antes de chamar o plugin nativo.

## 5. Integridade e segurança

O bundle só é baixado com checksum e assinatura encaminhados ao plugin Live Update. Nenhuma chave privada entra no Web, APK ou site Netlify. O cliente não instala bundle antes das validações. A política nativa `autoBlockRolledBackBundles` permanece preservada, e `LiveUpdate.ready()` é chamado depois da pintura inicial para que o plugin possa confirmar o bundle ou executar o rollback nativo sem bloquear o startup.

## 6. Erros observáveis

Os erros são persistidos sem tokens ou dados pessoais. Os códigos incluem `OTA_MANIFEST_INVALID`, `OTA_MANIFEST_UNAVAILABLE`, `OTA_CHANNEL_MISMATCH`, `OTA_NATIVE_VERSION_MISMATCH`, `OTA_RUNTIME_MISMATCH`, `OTA_BUNDLE_INVALID`, `OTA_DOWNLOAD_FAILED`, `OTA_CHECKSUM_INVALID`, `OTA_SIGNATURE_INVALID`, `OTA_ORIGIN_INVALID`, `OTA_INSTALL_FAILED`, `OTA_STAGING_FAILED`, `OTA_ROLLBACK`, `OTA_ALREADY_STAGED`, `OTA_CONCURRENT_CHECK` e `OTA_UNKNOWN_ERROR`.

O diagnóstico técnico está disponível por `getOtaDiagnostic()` e contém fase, código, timestamps, versão/canal nativos, runtime, bundles atual/próximo e identidade do manifesto. O usuário não recebe stack trace.

## 7. Concorrência e lifecycle

`OtaManager.check()` usa uma promessa única para impedir checks, downloads e staging concorrentes. Startup, resume e intervalo passam pelo mesmo método. Startup e resume bypassam apenas o throttle de quinze minutos para reagir a uma retomada real; o intervalo respeita o throttle. A autoridade registra listeners de lifecycle uma única vez.

## 8. Viagens e aplicação

O OTA pode verificar, baixar e preparar durante uma viagem, mas nunca recarrega a página, limpa sessão, troca o bundle em execução ou reinicia o aplicativo. A aplicação acontece somente após fechamento e reabertura do APK.

## 9. Publicação correta

1. Atualizar o código Web e manter `package.json.gtoWebRuntimeRevision` coerente.
2. Executar `npm run build`.
3. Confirmar `dist/nvu-build.json`.
4. Gerar o bundle com o empacotador oficial.
5. Preparar o site com `prepare-netlify-ota.mjs`, usando manifesto HTTPS e chave privada apenas fora do projeto.
6. Executar `verify:ota-ready`, `verify:release` e o validador público do Netlify.
7. Publicar o site somente depois de checksum, assinatura, canal, versionCode, runtime e ZIP passarem.
8. Para mudança Web-only, usar o canal do APK nativo já instalado. Só gerar APK se houver mudança nativa real ou se a base instalada não puder bootstrapar o OTA.
