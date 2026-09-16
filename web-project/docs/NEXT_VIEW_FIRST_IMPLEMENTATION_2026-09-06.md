# Implementação Next View First — fluxo crítico NVU

**Data:** 2026-09-06  
**Escopo:** Auth → Profile Index → Active Profile Context → seleção → navegação inicial.  
**Fora do escopo:** OTA, Capacitor/Android, ranking, histórico, dashboard secundário, redesign visual e deploy.

## Resultado

A etapa prática foi implementada de forma incremental no Web local. O fluxo crítico agora deriva os perfis válidos antes de tornar o seletor interativo, navega imediatamente a partir do perfil indexado e carrega persistência de role/empresa em background através do `switchRole` já existente.

Não foi gerado APK. Não foi publicado deploy. Não foi alterado o comportamento do OTA.

## Arquivos alterados

| Arquivo | Responsabilidade da alteração |
| --- | --- |
| `src/services/profileIndex.ts` | Novo resolver puro de perfis válidos e `ActiveProfileContext`; não faz rede, refresh ou navegação. |
| `src/context/AppContext.tsx` | Deriva `profileIndex` e `activeProfileContext` dos snapshots já existentes e os expõe no SessionStore. |
| `src/contexts/ProfileSessionProvider.tsx` | Expõe o contexto ativo na facade de perfil existente; nenhum provider novo foi criado. |
| `src/lib/simulatorOptions.ts` | Adiciona `resolveRegisteredSimulatorId`, que só aceita correspondência no catálogo canônico. |
| `src/pages/SelectProfile.tsx` | Consome Profile Index; elimina pending intent, refresh/hydration iniciados pelo clique e autoabre o único perfil válido. |
| `src/App.tsx` | Centraliza a decisão inicial: Profile Index pronto com um perfil abre o destino; múltiplos vão ao seletor; memória de rota deixa de decidir a entrada. |
| `src/pages/admin/DriverProfileIsolated.tsx` | Remove fallback de identidade `G. Truck`; simulador ausente do catálogo aparece como “Simulador não vinculado” e não como identidade operacional válida. |
| `package.json` | Registra `test:profile-index` em `verify:login`. |

## Arquivos de testes ajustados ou adicionados

`src/services/profileIndex.ts` é coberto por `scripts/test-profile-index.mjs`, com cenários de resolução pendente, um perfil, membership inativa, simulador desconhecido e múltiplos perfis.

Os gates migrados para o contrato novo foram `test-login-profile-recovery.mjs`, `test-login-profile-flow.mjs`, `test-profile-fast-access.mjs`, `test-auth-instant-path.mjs`, `test-startup-profile-selection.mjs`, `test-first-login-onboarding.mjs`, `test-session-boot-foundation.mjs`, `test-auth-session-stability.mjs`, `test-profile-operation-instant-surface.mjs`, `test-persistent-warm-boot.mjs` e `test-active-profile-resume.mjs`.

As alterações nesses gates não desativam verificações de segurança; elas removem expectativas do mecanismo antigo de `pendingProfileIntentRef`, hydration no clique e decisão por fallback de `activeRole`, substituindo-as por assertions de Profile Index, destino canônico e ausência de refresh no bloco de navegação.

## O que foi removido do caminho do clique

O `SelectProfile` não mantém mais `pendingProfileIntentRef`, `profileRefreshInFlightRef` ou `pendingProfileRole`. O primeiro toque não inicia `refreshSession`, não busca membership, não hidrata empresa, não espera listener e não agenda effect para navegar. Se um botão é renderizado, seu perfil já está presente no `ProfileIndex` com `valid=true`.

A hydration de empresa por `loadCompanyById` foi retirada do SelectProfile. A empresa pode continuar sendo carregada pelo contexto existente e pelas telas que realmente precisam dela, mas a ação de seleção não depende desse trabalho secundário.

A decisão inicial deixou de restaurar uma rota por `activeRole` antes de saber quantos perfis válidos existem. A memória de rota continua UID-scoped e persistente para registro/uso posterior, mas não concorre com a decisão inicial.

O fallback textual `G. Truck` foi removido do perfil isolado. O resolver estrito não transforma texto arbitrário ou alias de company em ID canônico.

## O que foi mantido temporariamente

`ProtectedRoute`, `sessionReady`, `identityReconciliationStatus`, `membershipsLoaded`, `MembershipProvider`, `CompanyContext` e a autoridade `switchRole` continuam presentes. Eles permanecem porque ainda protegem autorização, reconciliação e compatibilidade com as páginas existentes. Nesta etapa, o Profile Index reduz o caminho crítico sem reescrever o projeto inteiro.

A persistência de role/empresa e o `updateDoc` de background dentro de `switchRole` foram mantidos. O contexto ativo é atualizado sincronamente para a navegação, enquanto a persistência não bloqueia a abertura da experiência.

## Fluxo antigo versus novo

```text
ANTES
Auth confirmada
  ↓
UI/SESSION flags
  ↓
SelectProfile pinta cache
  ↓
Clique
  ↓
verifica sessionReady
  ↓
pendingProfileIntentRef
  ↓
refreshSession/recovery
  ↓
listener/membership
  ↓
effect consome intenção
  ↓
navigate
```

```text
DEPOIS
Auth UID
  ↓
Profile Index canônico
  - memberships ativas
  - role válido
  - companyId
  - simulatorId somente se registrado
  - destination
  ↓
DECIDE
  - 0 perfis: estado vazio/diagnóstico
  - 1 perfil: autoabertura
  - 2+ perfis: seletor interativo
  ↓
Clique em perfil já válido
  ↓
Active Profile Context síncrono
  ↓
navigate(destination)
  ↓
persistência e dados secundários em background
```

## Testes executados

| Verificação | Resultado |
| --- | --- |
| `npm run lint` | Aprovado |
| `npm run test:profile-index` | Aprovado — 5 cenários |
| `npm run verify:login` | Aprovado — suíte completa de login/sessão/perfil |
| `npm run test:profile-fast-access` | Aprovado — 12 checks |
| `npm run test:auth-instant-path` | Aprovado — 16 checks |
| `npm run test:startup-profile-selection` | Aprovado |
| `npm run test:first-login-onboarding` | Aprovado |
| `npm run test:session-boot-foundation` | Aprovado |
| `npm run test:persistent-warm-boot` | Aprovado |
| `npm run test:active-profile-resume` | Aprovado |
| `npm run test:profile-operation-instant-surface` | Aprovado — 28 checks |
| `npm run verify:release` | Aprovado |
| `npm run build` | Aprovado; manifesto Web 2.3.51 gerado |

O build emitiu apenas o aviso conhecido de chunks grandes do bundle principal; não houve erro de TypeScript, build ou gate. A validação foi estrutural/local; não houve login físico em dispositivo Android, teste ADB ou comprovação da OTA no aparelho.

## Métricas da etapa

| Medida | Antes | Depois desta etapa |
| --- | ---: | ---: |
| Pending intent no SelectProfile | 1 mecanismo | 0 |
| Refresh iniciado pelo clique | 1 caminho | 0 |
| Hydration de empresa iniciada pelo seletor | 1 caminho | 0 |
| Resolver canônico de perfis | inexistente como contrato único | 1 `ProfileIndex` |
| Contexto mínimo pós-seleção | implícito em role/company | 1 `ActiveProfileContext` derivado |
| Decisão inicial por cardinalidade de perfis | distribuída | centralizada no AppRouteContent/Profile Index |
| Fallback textual de simulador no perfil isolado | `G. Truck` | “Simulador não vinculado” |
| Providers novos | — | 0 |
| Listeners novos | — | 0 |
| Consultas Firestore novas | — | 0 |

## Riscos restantes

A autorização canônica ainda é confirmada pelo `ProtectedRoute` e pelo `switchRole`; portanto, a validação em dispositivo deve confirmar que um membership ativo chega ao Profile Index antes de o seletor ser mostrado. O caso de membership legada sem `simulatorId` e company ainda não hidratada não pode inventar um simulador; ele permanece sem label canônico até o catálogo ser resolvido.

A Web pública atualmente continua na versão/deploy anterior porque nenhum deploy foi feito nesta etapa. O Android HF208 também não foi alterado. O próximo passo seguro, se desejado, é revisar este diff e executar um teste Web autenticado; somente depois deve-se decidir se a alteração deve receber uma nova versão Web e passar por publicação OTA.

> **Critério de aprovação da etapa:** nenhum perfil é mostrado como interativo se a abertura depender de trabalho iniciado após o toque; a decisão inicial usa um contexto pequeno e canônico; e dados secundários não bloqueiam a próxima visualização.
