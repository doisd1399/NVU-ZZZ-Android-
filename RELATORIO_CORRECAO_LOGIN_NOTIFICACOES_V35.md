# Relatório — Correção de Login e Notificações NVU v35

## Arquivo-base

`nvu-zzz (34).zip`

## Causas-raiz confirmadas

1. O Painel Sênior criava notificações diretamente no Firestore com o formato legado (`dataHora`, `tipo`, `lida`) e sem os campos canônicos do serviço central.
2. O listener global examinava toda a lista de notificações não lidas após cada montagem. O controle contra repetição existia apenas em memória e era perdido ao recarregar o app.
3. Não existia uma separação confiável entre o primeiro snapshot (histórico) e documentos realmente novos.
4. O perfil ativo ausente era interpretado como perfil corporativo, e notificações vinculadas a empresa podiam passar sem empresa ativa definida.
5. Popups podiam surgir em `/login`, `/select-profile` e `/status`.
6. O login redirecionava para `/status` ao encontrar qualquer inscrição antiga, inclusive aprovada ou recusada.
7. O botão de login podia permanecer em loading quando o Firebase Auth concluía, mas a unificação do documento falhava.
8. A unificação de usuários duplicados removia documentos antigos depois de migrar somente `trabalhos` e `companyMembers`, deixando outras referências órfãs.
9. A tela de status usava o primeiro registro encontrado, sem ordenar nem priorizar inscrição pendente.

## Correções aplicadas

### Notificações

- `SeniorPanel` passou a usar exclusivamente `createNotification()`.
- Aprovação e recusa usam tipos canônicos, `targetProfile`, `dedupeKey`, `createdAt`, `createdAtIso`, `read/lida` e versão de schema.
- Criado sinal `notificationsHydrated` no `AppContext`.
- O primeiro snapshot das coleções `notifications` e `notificacoes` apenas hidrata o sino; não gera popup.
- O listener foi movido para dentro do `BrowserRouter` e passou a considerar a rota atual.
- Popups são permitidos somente em rotas operacionais (`/admin`, `/driver`, `/ranking`).
- Login, seletor de perfil, status e formulários públicos não exibem notificações repentinas.
- Notificações vinculadas a empresa exigem empresa ativa correspondente.
- Sem perfil ativo, nenhuma notificação é considerada visível.
- Timestamps legados (`dataHora`, `data`) são normalizados.
- Apenas notificações realmente recentes podem gerar popup.
- O campo persistente `popupShownAt/popupShownAtIso` impede repetição em novas sessões.
- O botão “Ver” marca a notificação como lida antes de abrir o destino.
- Atualizações silenciosas de popup não geram novos toasts de erro.

### Login e identidade

- O redirecionamento para `/status` ocorre somente quando existe inscrição `pending`.
- O fluxo de navegação fica suspenso enquanto a autenticação/unificação está em andamento, evitando corrida com o listener do Firebase Auth.
- `setLoading(false)` agora é executado sempre no `finally`.
- Criado `userIdentityService.ts` para unificação canônica e segura do usuário.
- Dados úteis, foto, funções, vínculos e memberships são preservados.
- Referências são migradas em lotes nas coleções:
  - `trabalhos`
  - `companyMembers`
  - `notifications`
  - `notificacoes`
  - `recruitment_applications`
  - `jobDemands`
  - `solicitacoes_motoristas`
  - `historico_viagens`
  - `simulator_members`
  - `frotas`
- O documento antigo só é removido após a migração completa das referências conhecidas.
- Em falha parcial, ele é mantido com `mergePending: true`, evitando perda de dados.
- `ApplicationStatus` prioriza inscrição pendente e, na ausência dela, usa o registro mais recente.
- Nova inscrição volta ao fluxo canônico `/apply`.

## Arquivos alterados

- `src/App.tsx`
- `src/components/NotificationToastListener.tsx`
- `src/context/AppContext.tsx`
- `src/lib/notificationScope.ts`
- `src/pages/ApplicationStatus.tsx`
- `src/pages/Login.tsx`
- `src/pages/admin/SeniorPanel.tsx`
- `src/services/userIdentityService.ts` (novo)
- `test_notification_flow.ts`
- `test_notification_wiring.ts`
- `test_login_notification_regression.ts` (novo)
- `package.json`
- `tsconfig.json`

Os equivalentes dentro de `nvu26/` foram sincronizados para evitar divergência entre as duas árvores existentes no pacote.

## Validações executadas

- `tsc --noEmit`: aprovado, sem erros.
- `npm run test:notifications-all`: aprovado.
  - escopo e normalização de notificações;
  - timestamp legado;
  - política de popup por rota, idade e leitura;
  - persistência moderna/legada;
  - wiring do listener e produtores;
  - login, status e migração não destrutiva.
- `npm run build`: aprovado.
- Vite: 3.292 módulos transformados.
- Build do servidor: aprovado.
- Testes de regressão selecionados de ranking/perfil executados sem alteração na lógica auditada:
  - ranking por período básico;
  - escopo interno/global do motorista;
  - diferença e próximo colocado;
  - escopo corporativo;
  - concorrente canônico;
  - alinhamento perfil/ranking;
  - foto após aprovação.

## Observação fora do escopo

O teste legado `test_company_period_ranking.ts` já falhava no arquivo original com ausência de `company-former` na população. A falha foi reproduzida no original e não foi causada por esta correção. A lógica de ranking não foi alterada.

## Resultado esperado

- Nenhuma notificação antiga surge ao abrir login, seletor de perfil ou status.
- Uma notificação nova aparece no máximo uma vez, somente no painel operacional correto.
- A notificação continua disponível no sino até ser lida.
- Login não fica travado em loading.
- Inscrições recusadas/aprovadas antigas não forçam `/status`.
- Usuários duplicados são unificados sem apagar dados antes da migração das referências.
