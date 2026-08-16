# Relatório de Auditoria e Correção: NVU v10

## Bug 1: Foto do motorista não aparecendo no menu lateral superior
### Causa Raiz
No componente `DriverLayout.tsx`, algumas tags de renderização de imagem e fallback (linhas 320 e 542) ainda utilizavam estritamente `currentUser?.profilePhotoURL`, ignorando as chaves herdadas ou campos legados (como `photoURL` ou `avatar`).
### Correção Aplicada
As ocorrências diretas de `profilePhotoURL` foram substituídas por `resolveDriverPhoto(currentUser)` no `DriverLayout.tsx`. O componente agora exibe as mesmas imagens que o seletor de perfil e o app context preloaders.

## Bug 2: Spam da notificação "Solicitação recusada" ao abrir Seletor de Perfil
### Causa Raiz
Havia duplicidade e erro de escopo no gerenciamento de Popups (toasts) das notificações:
1. O hook `useEffect` responsável pelas assinaturas do Firestore em `AppContext.tsx` disparava manualmente a função `toast` cada vez que os listeners recarregavam (o que ocorre sempre que o perfil ou a empresa ativa são alterados, redefinindo `initializedSources`).
2. O cache local que deveria impedir a repetição (`shownNotificationPopupsRef`) era perdido nas recargas de rotas específicas, e a validação de temporalidade ("somente não lidas" e "novas") não era adequadamente bloqueada no AppContext.
### Correção Aplicada
- O bloco de disparo de popups de notificação foi removido do `AppContext.tsx`. Ele causava "race conditions" e repetições cada vez que o contexto era repopulado.
- O componente isolado `NotificationToastListener.tsx` foi reescrito para centralizar *com exclusividade* toda a renderização visual das notificações (Toasts).
- O `NotificationToastListener` agora processa a coleção ativa e valida se a data da notificação é menor que 15 segundos da data/hora atual. Ele mantém um set inquebrável de IDs vizualizados atrelados diretamente ao ciclo de vida raiz da aplicação (App.tsx), eliminando repetições mesmo em navegação.

## Restrições Preservadas e Verificadas
- `simulatorId` intocado.
- Lógicas e mecânicas de `ranking` intocadas.
- Lógicas e arquitetura de `histórico` não alteradas.
- Fluxos de `operações` sem alterações estruturais.
- Regras de negócio perfeitamente mantidas (apenas os popups visuais sofreram refatoração arquitetural de listeners).
