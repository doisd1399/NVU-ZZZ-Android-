# Relatório de Migração de Simuladores - Etapa 1

## Arquivos Alterados
1. \`src/context/AppContext.tsx\`

## Detalhes das Alterações

### Criação de Estrutura de Tipagem e Estado
- **Interface**: Criada e exportada a interface \`Simulator\` definindo os campos:
  - \`id\`: Identificador único (gerado pelo Firestore)
  - \`name\`: Nome do simulador
  - \`active\`: Status do simulador (boolean)
  - \`createdAt\`: Data de criação
  - \`updatedAt\`: Data de última modificação
- **Contexto (AppContextType)**: O estado \`simulators\` foi adicionado à tipagem para torná-lo acessível por todos os componentes da aplicação, provendo um array da interface \`Simulator\`.
- **Provedor (AppProvider)**: Inicializado o estado com \`useState<Simulator[]>([])\` e injetado o valor de \`simulators\` no contexto.

### Rotina de Inscrição e Semeamento (Seeding)
- **Subscription em Tempo Real**: Adicionado um novo \`useEffect\` no \`AppProvider\` contendo um bloco de \`onSnapshot\` para realizar a escuta da coleção \`simulators\` no Firestore, preenchendo a variável do estado de modo reativo.
- **Auto-criação Inicial**: Inserida lógica no retorno do snapshot garantindo que se a coleção se encontrar vazia (\`snap.empty\`), os simuladores padrão (\`"GTO"\`, \`"WTDS"\`, \`"WBDS"\`, \`"TOE 3"\`, \`"ETS 2"\`, \`"ATS"\`, \`"PBS"\`) serão preenchidos de forma transacional gerando novos documentos, construindo a coleção no banco e repopulando o estado na sequência.

## Restrições Atendidas
- Fluxos de cadastro da empresa, inscrição do motorista e cálculos de ranking **permanecem inalterados**. 
- Nenhuma base de dados (empresas ou motoristas) existente foi alterada ou comprometida.
- Nenhum campo local ou metadado das interfaces existentes foi suprimido.
