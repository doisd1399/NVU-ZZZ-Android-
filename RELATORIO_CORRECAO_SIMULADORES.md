# Relatório de Correções de Fluxo Simulador → Empresa

## 1. Arquivos alterados
- `src/pages/driver/JoinCompany.tsx`
- `src/pages/RegisterCompany.tsx`
- `src/context/AppContext.tsx`
- `src/services/notificationService.ts` (tipos de notificação adicionados anteriormente)

## 2. Causa raiz encontrada
- A tela de inscrição de motoristas exibia todas as empresas cadastradas de todos os simuladores juntas, dependendo do campo de busca textual. Não existia uma etapa prévia para a escolha do simulador, o que permitia que um motorista de um simulador visualizasse e enviasse solicitações para empresas focadas em outro simulador.
- Na hora de efetuar a solicitação (`requestJoinCompany`), o ID do simulador correspondente à empresa não estava sendo salvo na solicitação.
- O cadastro da empresa usava uma lógica customizada em vez de utilizar o estado global consolidado para validar simuladores vazios e prevenir submissão sem simulador.

## 3. Alterações aplicadas
- **JoinCompany (Inscrição Motorista)**: O fluxo foi modificado e um "select" de simuladores foi introduzido antes do campo de busca de empresas. As empresas só são liberadas para pesquisa (e visualização) após o motorista escolher o simulador e o filtro (`simulatorId || resolveSimulatorId`) for satisfeito. Adicionado fallback para quando nenhum simulador está selecionado ou quando a busca não retorna itens.
- **RegisterCompany (Cadastro Empresa)**: A tela de registro agora exibe uma mensagem específica ("Nenhum simulador disponível no sistema.") e bloqueia o botão de envio (disabled) caso o array global de simuladores não retorne dados ou caso o usuário não tenha selecionado um.
- **AppContext (Central de Requisições)**: O payload enviado pelo motorista para `solicitacoes_motoristas` foi atualizado para carregar a trinca correta e evitar cruzamentos incorretos: `{ motoristaId, empresaId, simulatorId }`. Além disso, a tipagem `DriverRequest` foi atualizada.
- A fonte oficial global (`simulators`) proveniente do Firebase no AppContext.tsx já estava populada corretamente devido a correções recentes e está sendo reutilizada em ambos os componentes, validando o campo `active !== false`.

## 4. Testes realizados
- Teste de interface do Inscrição Motorista (JoinCompany): A lista de simuladores abriu corretamente e o botão foi exibido em estado inativo antes da seleção de um simulador.
- Teste de busca restrita: Após selecionar o simulador, a lista renderizou somente empresas relativas ao simulador filtrado, bloqueando a visão de outros simuladores.
- Teste de layout de erro de simuladores ausentes no cadastro de empresa.
- Validação na submissão de `solicitacoes_motoristas` com a propriedade `simulatorId`.

## 5. Confirmação de impacto nulo
- Nenhum tipo de modificação foi efetuada nos mecanismos de operações em tempo real, cálculos de ranking de simulação e frotas, tabelas históricas (historicoTrips) ou regras financeiras da plataforma. A modificação foi isolada ao fluxo inicial de entrada do usuário.
