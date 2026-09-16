# Relatório — Google Auth Web 2.3.51

**Projeto:** NVU Web  
**Domínio-alvo:** `https://stirring-pavlova-ca6808.netlify.app/`  
**Firebase project:** `vtc-frota-log`  
**Data:** 2026-09-06

## 1. Diagnóstico

A implementação Web utilizava `google.accounts.id.prompt()` como mecanismo efetivo de início do login. O GIS estava carregado e `window.google.accounts.id` existia, mas a tentativa pública retornou `displayed: false`, `skipped: true` e `skippedReason: "unknown_reason"`.

O fluxo convertia esse resultado em `GOOGLE_IDENTITY_NOT_DISPLAYED` e encerrava a tentativa. Não havia uma alternativa determinística para abrir o seletor de conta. A causa raiz funcional é a dependência exclusiva do prompt One Tap/GIS, que é uma camada opcional de UX e pode ser ignorada pelo navegador, pela sessão Google ou pela configuração de origem.

O motivo externo exato de `unknown_reason` não foi atribuído de forma conclusiva apenas pela API pública. A mensagem anterior sugeria domínio autorizado, mas não provava sozinha `auth/unauthorized-domain`.

## 2. Configuração verificada

O bundle Web compilou `authDomain: vtc-frota-log.firebaseapp.com`, `projectId: vtc-frota-log` e o client ID Web Google correspondente ao projeto. O domínio público carregou o GIS em `https://accounts.google.com/gsi/client`.

O build público anterior confirmou `otaEnabled: true`, runtime `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR` e canal `production-286`. A configuração administrativa de Authorized Domains/Google Provider do Firebase não pôde ser lida por API pública sem credencial administrativa; por isso, não foi declarada como aprovada apenas por inferência.

## 3. Código alterado

Foram alterados exclusivamente:

| Arquivo | Alteração |
| --- | --- |
| `src/services/googleAuthService.ts` | Adicionado `signInWithPopup` como fallback Web somente quando o GIS retorna `GOOGLE_IDENTITY_NOT_DISPLAYED`. O Android continua usando `signInWithNative`. Nenhum redirect foi introduzido. |
| `src/pages/Login.tsx` | Tratamento explícito de `auth/popup-blocked`, `auth/popup-closed-by-user`, `auth/unauthorized-domain`, `auth/operation-not-allowed` e `auth/network-request-failed`, com mensagens amigáveis e sem stack trace. |
| `scripts/test-google-auth-state.mjs` | Cobertura estrutural do fallback popup e dos códigos de erro Firebase. |
| `scripts/test-login-identity-reconciliation.mjs` | Gate ajustado para exigir GIS primeiro + fallback popup, mantendo a proibição de redirect. |

A máquina de estados existente continua sendo a autoridade única. O fallback cria uma nova tentativa somente depois que a tentativa GIS já foi encerrada, impedindo GIS e popup simultâneos, loops e múltiplos locks.

## 4. Testes locais

| Comando | Resultado |
| --- | --- |
| `npm run lint` | Aprovado |
| `npm run test:google-auth-state` | Aprovado: 3 cenários + fallback popup + mensagens Firebase |
| `npm run test:login-identity-reconciliation` | Aprovado: 6 fixtures + 20 checks |
| `npm run test:auth-session-stability` | Aprovado |
| `npm run test:login-profile-flow` | Aprovado: 8/8 |
| `npm run test:login-profile-recovery` | Aprovado |
| `npm run verify:login` | Aprovado |
| `npm run verify:release` | Aprovado |
| `npm run build` | Aprovado; Web 2.3.51 |

O build local gerou `nvu-build.json` com Web `2.3.51`, runtime correto e OTA habilitada. O aviso de chunks grandes do Vite permaneceu apenas como warning de empacotamento, sem falha.

## 5. Produção

Foi preparado um staging somente Web que substitui a raiz da aplicação pelo build corrigido e preserva os diretórios OTA históricos sem modificar seus bundles ou manifests.

A tentativa de publicação no site Netlify existente falhou com:

```text
401 Unauthorized
```

O domínio público foi reconsultado após essa falha e permanece servindo o build anterior, com `buildId: local-2026-09-06T11:18:33.682Z`. Portanto, a correção popup **não está publicada** e não foi possível repetir o login real contra o código corrigido.

## 6. Next View First

O código do Profile Index, Active Profile Context, SelectProfile, App e navegação não foi alterado nesta etapa. Os gates de login/reconciliação continuam aprovados. Como a autenticação pública não foi concluída com o build corrigido, não há declaração de validação funcional de ponta a ponta em produção.

## 7. Escopo

| Item | Resultado |
| --- | --- |
| Android alterado | **NÃO** |
| APK gerado | **NÃO** |
| OTA alterado | **NÃO** |
| Live Update alterado | **NÃO** |
| Profile Index alterado | **NÃO** |
| Active Profile Context alterado | **NÃO** |
| Next View First alterado | **NÃO** |
| Google Auth Web corrigido localmente | **SIM** |
| Web público validado com a correção | **NÃO** |
| Web público anterior acessível | **SIM** |
| Deploy corrigido concluído | **NÃO — 401 Unauthorized** |

## 8. Decisão

> **REPROVADO — ainda não avançar para OTA/Android; causa pendente: a correção local está aprovada, mas o deploy Netlify foi rejeitado por `401 Unauthorized` e a configuração administrativa de Authorized Domains/Google Provider não pôde ser confirmada.**

Não foi gerado novo HF, APK ou bundle OTA. O próximo passo é reautenticar/autorizar o conector Netlify e confirmar no Firebase Console que `stirring-pavlova-ca6808.netlify.app` está autorizado e que o provedor Google está habilitado. Depois disso, publicar somente a raiz Web corrigida e repetir o login público; somente se essa sessão confirmar Firebase Auth e chegar ao Profile Index será possível declarar aprovação.

## Referências

[1]: https://stirring-pavlova-ca6808.netlify.app/ — domínio público NVU.

[2]: https://stirring-pavlova-ca6808.netlify.app/nvu-build.json — build público atual.

[3]: https://stirring-pavlova-ca6808.netlify.app/ota/production-286/manifest.json — manifesto público production-286.

[4]: https://accounts.google.com/gsi/client — Google Identity Services carregado pelo Web.

## Reexecução operacional do prompt

Em nova execução, a leitura de identidade do conector Netlify (`get-user`) retornou HTTP 401 antes de qualquer nova publicação. Isso confirma independentemente que a sessão/conector atual não possui autorização válida para consultar/publicar no Netlify; o bloqueio não foi causado pelo diretório Web corrigido, pelo site ID informado ou pelo conteúdo do staging.

Conforme a regra absoluta do prompt, a operação foi interrompida nesse ponto. Não foram feitas novas tentativas de contorno, não foi criado outro site, não foram alterados tokens/segredos e não houve publicação parcial.

**Status obrigatório:** `DEPLOY BLOQUEADO — NETLIFY 401`.

## Publicação corrigida após autorização

Após a autorização pelo CLI oficial, o site existente foi vinculado e o deploy Web foi publicado com sucesso:

- Deploy ID: `6a9d72c1fd65e3daa7763515`
- URL pública: `https://stirring-pavlova-ca6808.netlify.app`
- URL imutável: `https://6a9d72c1fd65e3daa7763515--stirring-pavlova-ca6808.netlify.app`

A primeira tentativa via staging incorreto preservava a Dist dentro de `staging/dist`. Ela não foi considerada válida. O staging foi reconstruído com a Dist corrigida diretamente na raiz e com `ota/production-286/` preservado; o segundo deploy foi o utilizado para validação.

A leitura direta por `curl` confirmou que o alias público e a URL imutável agora servem `nvu-build.json` com `buildId: local-2026-09-06T13:21:05.346Z`, Web `2.3.51`, `otaEnabled: true` e o mesmo runtime. A extração anterior apresentou conteúdo antigo em cache; os corpos diretos e os cabeçalhos `no-cache,no-store,must-revalidate` confirmaram o build novo.
