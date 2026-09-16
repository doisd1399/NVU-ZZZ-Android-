# Auditoria — atraso entre seleção e abertura do perfil

## Decisão

**REPROVADO — não aplicar correção especulativa e não gerar APK.** A relação com OTA foi descartada estruturalmente, mas ainda não há uma medição autenticada T0–T11 em dispositivo/WebView que permita afirmar qual etapa consumiu os milissegundos observados.

## Fluxo comprovado no código

| Etapa | Arquivo/função | Operação | Bloqueia? | Evidência |
| --- | --- | --- | --- | --- |
| T0 | `SelectProfile.tsx` / `handleSelect` | Localiza o perfil já presente no `ProfileIndex` | Não | Busca síncrona no array |
| T1 | `SelectProfile.tsx` / `commitProfileNavigation` | Chama `void switchRole(...)` | Não | Não existe `await` |
| T2 | `AppContext.tsx` / `switchRole` | Valida membership/role em memória, grava estado/localStorage e inicia `updateDoc` em background | Não no caminho da UI | `updateDoc(...).catch(...)` não é aguardado |
| T3 | `SelectProfile.tsx` | `navigate(profile.destination)` | Não | Executado no mesmo handler após `switchRole` |
| T4 | `App.tsx` / `ProtectedRoute` | Avalia auth, `sessionUiReady`, active company/role e membership | Pode renderizar `RouteLoading` ou redirecionar | Guard retorna `RouteLoading` enquanto `sessionUiReady` é falso |
| T5–T7 | `AppContext.tsx` / `confirmCanonicalMemberships` | Consulta `getDocsFromServer(companyMembers)` | Pode atrasar reconciliação | Timeout definido em 12.000 ms |
| T8 | `App.tsx` / `RouteLoading` | Suspense/guard termina | Depende do guard/chunk | Fallback global genérico |
| T9–T10 | `routePreload.ts` e imports lazy | Carrega `AdminLayout`/`DriverLayout` e página de destino | Pode atrasar a pintura final em WebView lenta | Preload é best-effort e não é aguardado |
| T11 | Rota final | Perfil fica visível | Resultado | Não medido em sessão autenticada nesta execução |

## O que foi descartado

O OTA não está no caminho crítico: `main.tsx` monta React antes de chamar `startOtaManager()` em `requestAnimationFrame`; `LiveUpdate.ready()` não é aguardado; `SelectProfile` não chama o manager; `setNextBundle` não força reload; e não há `window.location.reload` no manager. Portanto, trocar a mensagem OTA não explica nem resolve sozinho o atraso entre toque e perfil.

## Candidatos reais restantes

O primeiro candidato é a divergência temporal entre o `ProfileIndex`, que pode estar pronto para a superfície visual, e o `ProtectedRoute`, que ainda precisa de `sessionUiReady`, `memberships` e `activeMembership` coerentes para autorizar a rota. O segundo candidato é o carregamento do chunk lazy do destino quando o usuário toca antes de o preload best-effort terminar. O código confirma a existência das duas etapas, mas não confirma qual delas ocorreu no caso relatado.

A confirmação server-side de memberships pode consumir até 12.000 ms quando é necessária, mas o valor real no caso do usuário não foi medido. Reduzir esse timeout ou removê-lo sem uma autorização canônica equivalente seria inseguro.

## Medição ainda necessária

É necessário executar uma sessão autenticada instrumentada, preferencialmente no HF208 WebView ou no Chrome Android com o fluxo equivalente, registrando T0–T11: toque, início/fim de `switchRole`, `navigate`, entrada/saída do `ProtectedRoute`, `sessionUiReady`, início/fim da membership, entrada/saída de `RouteLoading`, início/fim do chunk e primeira pintura do perfil.

Sem essa sessão, qualquer alteração no `ProtectedRoute`, bypass de membership, remoção de `RouteLoading` ou mudança no preload seria uma correção baseada em hipótese, explicitamente proibida pelo prompt.

## Escopo preservado

Nesta auditoria não houve alteração em Android, Capacitor, Gradle, AndroidManifest, versionCode, versionName, canal, keystore, OTA 2.3.52, Profile Index, Active Profile Context ou regras de negócio. Nenhum APK foi gerado e nenhum deploy foi feito.
