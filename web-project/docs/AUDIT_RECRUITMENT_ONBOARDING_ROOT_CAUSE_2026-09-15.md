# Auditoria do fluxo de inscrição e cadastro — 2026-09-15

## Escopo

Fluxo esperado: login Google; permanência no fluxo de inscrição/cadastro; envio da candidatura; redirecionamento para `/status`/pendências; somente após aprovação a criação/seleção do perfil operacional.

## Causas confirmadas no código

1. `src/pages/SelectProfile.tsx` calcula `serverEmptyConfirmed` somente com `identityReconciliationStatus`, `membershipsLoaded` e `sessionDiagnostic === MEMBERSHIP_SERVER_EMPTY`. O estado vazio é renderizado sem exigir que `useCurrentUserPendingApplications()` tenha concluído (`pendingApplicationsLoading === false`). Assim, uma conta autenticada sem membership pode mostrar o estado genérico `Sem vínculos` durante a janela em que a consulta da candidatura pendente ainda está vazia/carregando.

2. O `profileIndex` só conhece memberships ativas; ele não conhece candidaturas pendentes. Isso é correto para autorização, mas exige que a UI de onboarding tenha precedência própria antes do estado vazio.

3. O logout existente chama `GtoObserver.logoutCleanup()`, porém não há uma limpeza equivalente ao entrar nas rotas públicas de onboarding. Se uma versão anterior deixou o serviço/projeção nativa residual ativo, a nova sessão pode herdar a solicitação de compartilhamento fora do fluxo operacional. Os chamadores legítimos de projeção estão em `Dashboard`/`GtoObserverSetup` e `gtoWorkLauncher`; `/apply`, `/register-company`, `/status`, `/pending-applications` e `/select-profile` não deveriam solicitar projeção.

4. `RecruitmentApply.handleNext()` e `RegisterCompany.handleGoogleAccess()` preservam a sessão e não deveriam navegar para o seletor; contudo, a proteção contra projeção residual e a precedência de pendências precisam ser aplicadas no shell global para cobrir APKs antigos e remounts de rota.

## Correção planejada

- Aguardar o carregamento da consulta de candidaturas antes de renderizar `Sem vínculos`; durante essa janela mostrar estado de validação/pendência.
- Quando houver candidatura pendente, exibir o card de pendências e oferecer navegação para `/status`/pendências, sem criar ou abrir perfil operacional.
- Ao entrar em rotas públicas de onboarding/pendência, executar uma limpeza nativa best-effort (`logoutCleanup`) sem tocar em memberships nem iniciar qualquer automação. Essa exceção fica restrita às rotas públicas e não altera rotas Driver/Admin/Print.
- Adicionar testes estáticos e comportamentais para proteger a precedência e a ausência de solicitação de projeção no onboarding.
