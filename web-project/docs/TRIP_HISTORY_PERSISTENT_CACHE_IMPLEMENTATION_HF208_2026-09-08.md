# Implementação Web-only — Histórico persistente HF208

## Escopo

A correção foi aplicada diretamente na base atual HF208. Nenhum projeto paralelo foi criado, nenhum APK/AAB foi gerado e nenhuma configuração Android, Capacitor, OTA ou runtime remoto foi alterada.

A versão Web permaneceu `2.3.57`, o canal nativo permaneceu `production-286` e o build foi executado pelo pipeline oficial existente.

## Causa raiz comprovada

O hook principal `useTripHistory` mantinha o cache persistido em `sessionStorage`. Esse armazenamento desaparece quando o WebView é encerrado e recriado. Além disso, o cache tinha expiração destrutiva de dez minutos e, mesmo quando encontrava viagens persistidas, inicializava `loading: true` sem um estado explícito de `refreshing`.

O resultado era:

```text
APP FECHA
↓
SESSIONSTORAGE E MEMÓRIA SÃO PERDIDOS
↓
HISTÓRICO COMEÇA VAZIO
↓
SKELETON/LOADING
↓
LISTENER FIRESTORE
↓
DADOS APARECEM
```

## Implementação

| Arquivo | Alteração | Motivo |
| --- | --- | --- |
| `src/lib/tripHistoryPersistentCache.ts` | Novo repositório persistente versionado `nvu.trips.v2`, com chave UID+empresa, validação estrutural, retenção stale e limpeza por UID. | Garantir restauração após fechamento completo sem compartilhar dados entre usuários ou empresas. |
| `src/hooks/useTripHistory.ts` | Substituição do cache `sessionStorage` por `localStorage` através do repositório único; estado inicial cache-first; `loading` somente sem dados; `refreshing` quando há dados stale. | Renderizar dados conhecidos imediatamente e sincronizar Firestore em background. |
| `src/context/AppContext.tsx` | Logout explícito chama `clearTripHistoryCacheForUser(logoutUid)`. | Remover somente o escopo persistido do usuário que saiu. |
| `src/pages/driver/TripHistory.tsx` | Passa o UID da sessão ao hook e preserva o skeleton apenas quando `loading && finalTrips.length === 0`. | Impedir cache sem escopo e evitar loading sobre lista conhecida. |
| `src/pages/admin/DriverProfileIsolated.tsx` | Passa `currentUser.id` ao histórico observado. | Isolar o histórico por usuário e empresa. |
| `src/pages/admin/fleet/CompanyTab.tsx` | Passa `currentUser.id` ao hook. | Reutilizar o mesmo cache seguro no perfil da empresa. |
| `src/pages/admin/fleet/OperationsTab.tsx` | Passa `currentUser.id` ao hook. | Manter o histórico operacional no mesmo contrato. |
| `src/pages/admin/fleet/DriversTab.tsx` | Passa `currentUser.id` ao hook. | Garantir que a lista de motoristas aqueça somente o escopo correto. |
| `src/pages/admin/Operations.tsx` | Passa `currentUser.id` ao hook. | Preservar isolamento na tela de operações. |
| `src/pages/admin/Reports.tsx` | Passa `currentUser.id` ao hook. | Preservar isolamento nos relatórios. |
| `src/pages/AuditPage.tsx` | Passa `currentUser.id` ao hook. | Evitar leitura de cache sem escopo na auditoria. |
| `scripts/test-trip-history-persistent-cache.mjs` | Novo gate determinístico de cold start, stale, corrupção, UID/empresa e logout. | Validar o contrato persistente sem depender de Firebase real. |
| `scripts/test-driver-history-immediate-load.mjs` | Expectativas atualizadas para o contrato UID+empresa, `refreshing` e repositório persistente. | Remover somente expectativas textuais legadas. |
| `scripts/test-global-performance-fast-surface.mjs` | Expectativas atualizadas para `readTripHistoryCache`/`writeTripHistoryCache`. | Alinhar o gate ao novo contrato sem alterar a implementação. |
| `package.json` | Novo script `test:trip-history-persistent-cache` incluído em `verify:login` e `verify:release`. | Impedir regressão silenciosa. |

## Código antigo removido

Foram removidos do fluxo principal do `useTripHistory`:

- a chave `nvu.instant.v1.company-trips.*` baseada somente em empresa;
- a leitura e gravação em `sessionStorage`;
- a invalidação destrutiva após dez minutos;
- `readPersistedTrips` e `writePersistedTrips` locais ao hook;
- o estado que retornava `loading: true` mesmo com viagens persistidas;
- o cache sem UID, que não permitia validar isolamento por usuário.

Não foram removidos os listeners Firestore, a reconciliação de documentos legados, os filtros de empresa, os filtros de motorista, o `normalizeTrip`, o controle de logout ou a autoridade do servidor.

## Fluxo novo

```text
APP START
↓
RESTORE AUTH/CONTEXTO
↓
RESOLVE UID + COMPANY ID
↓
READ localStorage nvu.trips.v2:user:<UID>:company:<COMPANY>
↓
VALIDATE VERSION/SCOPE/STRUCTURE
↓
RENDER HISTÓRICO CONHECIDO IMEDIATAMENTE
↓
loading=false; refreshing=true
↓
LISTENER FIRESTORE NO MESMO COMPANY ID
↓
MERGE CANÔNICO + ALIASES LEGADOS
↓
ATUALIZA UI SOMENTE COM DATA CONFIRMADA
↓
GRAVA NOVO SNAPSHOT NO MESMO ESCOPO
```

## Segurança e isolamento

O cache exige simultaneamente `userId` e `companyId`. Um usuário diferente, uma empresa diferente, uma versão incompatível ou um objeto corrompido não produz dados visíveis. Dados stale podem alimentar a primeira pintura, mas não substituem autorização, memberships, `ProtectedRoute` ou as regras do Firestore.

No logout explícito, somente as chaves do UID encerrado são removidas. O cache de outro usuário não é apagado nem reutilizado. A troca de empresa muda a chave do escopo e impede que a lista anterior seja apresentada como pertencente à empresa nova.

## Testes executados

| Teste | Resultado |
| --- | --- |
| `test:trip-history-persistent-cache` | PASS — cold start, stale, UID/empresa, corrupção e limpeza escopada |
| `test:driver-history-immediate-load` | PASS |
| `test:driver-history-scenarios` | PASS — 7 cenários |
| `test:global-performance-fast-surface` | PASS |
| `verify:login` | PASS |
| `lint` / TypeScript | PASS |
| `verify:release` | PASS |
| Build Web oficial | PASS — Web `2.3.57` |

## Compatibilidade OTA

```text
NENHUM APK GERADO
NENHUMA ALTERAÇÃO NATIVA NECESSÁRIA
NENHUMA ALTERAÇÃO DE PACKAGE/APPLICATION ID
NENHUMA ALTERAÇÃO DE VERSIONCODE NATIVO
NENHUMA ALTERAÇÃO DO MECANISMO OTA
DIST GERADO COM SUCESSO
COMPATÍVEL COM A BASE APK ATUAL
PRONTO PARA DEPLOY OTA
```

A validação automatizada confirmou o contrato. Ainda é necessário testar fisicamente no WebView após fechamento completo, reabertura com sessão restaurada, troca de usuário e reconciliação com novos dados. A publicação OTA não foi executada nesta etapa.
