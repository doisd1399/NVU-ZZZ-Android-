# Evidências do incidente OTA/HF208 — 2026-09-06

## Sintomas relatados

No HF208, o aviso visual de atualização não apareceu e o seletor de perfil ficou sem resposta por alguns segundos.

## Causa OTA comprovada no artefato Web 2.3.49

O `src/lib/otaManager.ts` depende de `VITE_NVU_SELF_HOSTED_OTA_ENABLE` e `VITE_NVU_OTA_MANIFEST_URL` em `import.meta.env`. O `vite.config.ts` injetava apenas `VITE_NVU_RUNTIME_REVISION`; não havia fallback público nem definição declarativa para flag/URL.

O build local que foi publicado apresentou `nvu-build.json` correto em 2.3.49, porém o ambiente de build não continha `VITE_NVU_SELF_HOSTED_OTA_ENABLE` nem `VITE_NVU_OTA_MANIFEST_URL`, e os assets compilados não continham a URL do manifesto production-286. Como `OTA_ENABLED` resulta falso ou a URL fica vazia, `OtaManager.start()` retorna sem executar check, manifesto, download ou evento visual. Isso explica simultaneamente a ausência do aviso e a ausência de diagnóstico OTA de runtime.

O manifesto público e o ZIP estão íntegros; portanto o problema não é o Netlify, checksum ou assinatura do manifesto publicado. O problema é a configuração não compilada no cliente que deveria consultar o manifesto.

## Causa provável/comprovada do bloqueio temporário do seletor

`InitialBootOverlay` é montado globalmente, usa `position: fixed`, `inset: 0`, `z-index: 2300` e não possui `pointer-events-none`. Quando a rota inicial é `/admin`, `/driver` ou `/ranking`, ele permanece sobre toda a aplicação até `currentUser.id || sessionUiReady` e duas animações consecutivas sem camadas de loading. Em um retorno/deep-link que navega para `/select-profile`, esse overlay pode continuar interceptando toques mesmo com o seletor já pintado.

Independentemente do overlay, `SelectProfile` ainda pode pintar a superfície a partir do cache, mas a autorização canônica e `switchRole` dependem de `sessionReady`; o código registra uma intenção pendente e dispara `refreshSession()`. Essa espera é aceitável como autorização, mas não deve bloquear visualmente o seletor nem impedir o primeiro toque.

## Correção segura planejada

1. Tornar a configuração OTA Web declarativa e com fallback explícito para o canal HF208: flag pública habilitada e manifesto same-origin HTTPS production-286. A chave pública continua somente na configuração nativa; nenhuma chave privada será incluída.
2. Alterar o gate de release para falhar quando o build efetivo não contiver flag OTA habilitada e URL de manifesto compilada.
3. Tornar o overlay inicial não bloqueante para ponteiros quando uma rota interativa já estiver montada; preservar o overlay visual somente enquanto não houver usuário/estado visual e manter as autorizações em `ProtectedRoute`/`sessionReady`.
4. Executar lint, gates OTA/login/release, build, verificar os marcadores compilados, recriar bundle/manifesto e só então publicar um novo Web-only OTA para production-286.

Nenhuma regra de negócio, Firestore, GTO, OCR ou APK será alterada sem nova evidência.

## Indisponibilidade pós-publicação

Após o deploy final 2.3.50 ser aceito, o Netlify passou a responder HTTP 503 com `{"error":"usage_exceeded","message":"Usage exceeded"}` para a raiz, `nvu-build.json`, manifesto production-286 e manifesto histórico 282, inclusive na URL imutável do deploy. O staging local contém todos os arquivos e a validação pública completa havia passado antes da limitação de uso; a indisponibilidade atual é do serviço/limite do site, não de checksum, assinatura ou ausência de arquivo no staging.

## Segunda causa OTA comprovada no aviso visual

`LiveUpdateStatus` inicializa o estado uma única vez durante o primeiro render e instala o listener em `useEffect`, mas não reaplica `getLatestLiveUpdateStatus()` após instalar o listener. O `main.tsx` inicia o manager em `requestAnimationFrame`, uma janela em que o manager pode emitir `downloading`/`completed` antes dos passive effects do React. Se isso ocorrer, o evento é armazenado apenas no módulo e o componente permanece nulo, sem mostrar o aviso. A correção planejada é instalar o listener em `useLayoutEffect` e processar imediatamente o último detalhe após o registro, preservando o comportamento não bloqueante.
