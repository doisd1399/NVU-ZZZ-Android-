# Implementação de sessão offline — HF208

## Veredito

**Aprovada localmente para validação física; não publicada e não empacotada em APK nesta execução.**

## Causa raiz

A base possuía caches separados para usuário, memberships, empresas e simuladores. Em um cold boot offline, esses estados podiam ser restaurados em momentos diferentes. Assim, o usuário e as memberships podiam existir enquanto `CompanyContext` ainda não possuía o documento de `frotas/{companyId}`; `buildProfileIndex` então não conseguia formar um perfil válido. O Firebase Auth também dependia da abertura de persistência Web antes de confirmar a sessão, e o Firestore usava apenas a configuração padrão de cache em memória.

## Alterações aplicadas

| Área | Implementação |
| --- | --- |
| Snapshot atômico | Criado `src/lib/offlineSessionSnapshot.ts`, UID-scoped, versionado e validado estruturalmente. |
| Boot | `AppContext` lê primeiro o snapshot único e restaura user, memberships, empresas, simuladores, role, empresa ativa e contexto de perfil. |
| Empresas | `CompanyContext` hidrata empresas do snapshot antes do catálogo público e mantém cache antigo como fallback. |
| Simuladores | O estado inicial usa simuladores do snapshot e mantém o catálogo existente como fallback. |
| Persistência | O snapshot só é gravado após UID Firebase coerente, memberships ativas, empresas de todas as memberships e `ProfileIndex` pronto. |
| Segurança | Snapshot antigo é apenas visual. `sessionAuthorized`, memberships canônicas, `ProtectedRoute` e ações protegidas continuam server-authoritative. |
| Logout/troca de conta | A limpeza existente por `nvu.session.*` permanece ativa; a chave do snapshot é UID-scoped e não pode ser lida por outro UID. |
| Firestore | A instância única agora tenta `persistentLocalCache()` com `persistentMultipleTabManager()` e cai para `getFirestore(app)` se IndexedDB não estiver disponível. |

## Testes executados

`test:offline-session-snapshot` passou validando snapshot completo, snapshot stale após sete dias, isolamento entre UIDs, corrupção/estrutura parcial, versão incompatível e limpeza explícita. Também passaram `ProfileIndex`, `profile-session-gate`, `last-known-good-state-functional`, a bateria formal `verify:release`, TypeScript, lint e build Web.

A bateria formal também preservou os contratos de Auth/Google, logout/login, memberships, selector, GTO/OCR, histórico, ranking, OTA, sessão restaurada e segurança. Nenhum APK/AAB foi gerado e nenhum Web/OTA foi publicado.

## Limites

A validação física ainda é obrigatória: abrir a HF208 com internet, permitir que o snapshot seja gravado, encerrar o aplicativo, desativar rádio/rede, reabrir e confirmar selector/perfil/empresa, depois restaurar a rede e confirmar reconciliação. A implementação local não autoriza ações protegidas sem confirmação Firebase; offline deve restaurar a superfície visual, não permitir operações de escrita sem autoridade online.

## Arquivos alterados

- `src/lib/offlineSessionSnapshot.ts`
- `src/context/AppContext.tsx`
- `src/context/CompanyContext.tsx`
- `src/lib/firebase.ts`
- `scripts/test-offline-session-snapshot.mjs`
- `scripts/test-first-login-onboarding.mjs`
- `scripts/test-active-profile-resume.mjs`
- `package.json`

Nenhum keystore, senha, `google-services.json`, token ou configuração privada foi incluído no relatório.
