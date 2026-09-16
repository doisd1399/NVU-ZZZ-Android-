# Instrumentação temporária — atraso entre seleção e perfil

## Objetivo

Esta etapa mede o atraso sem alterar ProtectedRoute, sessionUiReady, membership, timeout de 12 segundos, Profile Index, Active Profile Context, switchRole, navigate, preload, lazy imports, OTA, Firebase rules ou Android.

A instrumentação é desativada por padrão. Ela só produz eventos quando a página define:

```js
window.__NVU_ENABLE_PROFILE_PERF__ = true;
```

Não grava UID, companyId, nomes de perfil ou dados de negócio. O trace fica apenas em memória e os eventos aparecem como `[PROFILE_PERF]` no console.

## Pontos implementados

| Evento | Local | Significado |
| --- | --- | --- |
| `T0_SELECT_PROFILE` | `SelectProfile.handleSelect` | Toque/click aceito em perfil validado pelo Profile Index |
| `T1_SWITCH_ROLE_START` | `SelectProfile.commitProfileNavigation` | Início da chamada síncrona de `switchRole` |
| `T2_SWITCH_ROLE_RETURN` | `SelectProfile.commitProfileNavigation` | Retorno da parte imediata de `switchRole`; não transforma a chamada em await |
| `T3_NAVIGATE_START/RETURN` | `SelectProfile.commitProfileNavigation` | Antes/depois de `navigate()` |
| `T4_PROTECTED_ROUTE_ENTER` | `ProtectedRoute` | Entrada da rota protegida e role esperada |
| `T5_SESSION_UI_NOT_READY/READY` | `ProtectedRoute` | Transições reais de `sessionUiReady` |
| `T6_MEMBERSHIP_START` | listener/recovery de memberships | Server-side start, cache-hit e quantidade em memória |
| `T7_MEMBERSHIP_END` | snapshot/recovery/error | sucesso, failure, timeout, count e server-side |
| `T8_ROUTE_LOADING_START/END` | `RouteLoading` | Tempo real do fallback e motivo (`auth-not-initialized`, `session-ui-not-ready`, `lazy-chunk-pending`) |
| `T9/T10_CHUNK` | `routePreload` | Início/fim do carregamento de chunk/preload |
| `PRELOAD_*` | `routePreload` | start/end/success/failure/cache-hit |
| `T11_PROFILE_FIRST_PAINT` | `driver/Profile` e `admin/Fleet` | `requestAnimationFrame` após o layout do shell de destino |

## Resumo matemático

O módulo `src/lib/profilePerformanceTelemetry.ts` expõe, enquanto habilitado:

```js
window.__NVU_PROFILE_PERF_READ__()
window.__NVU_PROFILE_PERF_SUMMARY__()
window.__NVU_PROFILE_PERF_RESET__()
```

`__NVU_PROFILE_PERF_SUMMARY__()` calcula `T1-T0`, `T2-T1`, `T3-T2`, `T3_RETURN-T3`, `T4-T3_RETURN`, `T5-T4`, `T6-T5`, `T7-T6`, `T8-T7`, `T8_END-T8`, `T9-T8`, `T10-T9`, `T11-T10` e o total `T11-T0` em milissegundos usando `performance.now()`.

## Validação técnica

`npm run verify:release` passou após a instrumentação, incluindo lint, build Web 2.3.52, login, Profile Index, Next View First, sessão, GTO, OCR, regras de rota/ganho, contrato OTA e status Live Update. Nenhum comportamento funcional foi alterado pelos marcadores.

O cenário autenticado obrigatório ainda não foi executado nesta sessão. O navegador de validação não possui uma sessão autenticada disponível e não houve acesso físico ao HF208/WebView. Portanto não existe ainda tabela real T0–T11 nem média/mínimo/máximo de uma sessão do usuário.

## Como medir uma sessão real

No Chrome Android ou em uma WebView de teste com o build instrumentado, abra o console, execute `window.__NVU_ENABLE_PROFILE_PERF__ = true`, faça login e selecione um perfil. Antes de cada execução, rode `window.__NVU_PROFILE_PERF_RESET__?.()`. Depois que o perfil aparecer, rode `window.__NVU_PROFILE_PERF_SUMMARY__?.()` e preserve o objeto retornado.

Executar três vezes em rede normal para perfil conhecido, repetir para segunda entrada e alternância A→B quando houver dois perfis. Se possível, repetir com latência controlada. Não publicar esse build instrumentado nem transformá-lo em APK.

## Decisão

**REPROVADO para diagnóstico final — instrumentação aprovada, medição real pendente.** Nenhuma correção de arquitetura foi aplicada porque ainda não há evidência matemática para classificar membership, ProtectedRoute/sessionUiReady, lazy chunk ou combinação como bloqueador principal.

OTA 2.3.52, Android, Capacitor, Firebase rules, Profile Index, Active Profile Context, versionCode, versionName e canal production-286 permanecem sem alteração nesta etapa. Nenhum APK, deploy ou release foi criado.
