# NVU R3.34-PC-HF11 — Google Sign-In Android

## Escopo

Correção nativa Android sobre a base aprovada R3.34-PC-HF10. Nenhum arquivo em `src/` ou `functions/` foi alterado.

## Causa raiz encontrada

O projeto usa `@capacitor-firebase/authentication` com o provedor `google.com`, mas `android/variables.gradle` não declarava as variáveis de build exigidas pelo setup Android do plugin para Google Sign-In. A ausência deixava as dependências do provedor Google/Credential Manager dependentes do ambiente/transitivos, gerando comportamento inconsistente entre aparelhos.

## Correção HF11

- `rgcfaIncludeGoogle = true`
- `androidxCredentialsVersion = '1.3.0'`
- Android `1.0.63` / `versionCode 63`
- gate `test:android-google-auth` adicionado ao `verify:release`
- workflow GitHub atualizado para validar e entregar HF11

## O que não mudou

- `src/`
- `functions/`
- Firebase project/appId
- URL remota do Netlify
- GTO/MediaProjection/seleção de frete/resultado/sincronização HF10

## Deploy necessário

Somente novo APK Android. Não é necessário novo deploy do Netlify, Google AI Studio ou Firebase Functions para esta correção.
