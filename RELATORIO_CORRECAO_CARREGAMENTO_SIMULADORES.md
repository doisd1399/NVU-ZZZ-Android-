# Relatório de Correção - Carregamento de Simuladores

## Causa Raiz
As telas públicas (Inscrição de Motorista e Cadastro de Empresa) recebiam a lista de simuladores vazia (`[]`) porque a assinatura (listener) do Firestore na coleção `simulators` (dentro de `src/context/AppContext.tsx`) dependia do ID do usuário logado (`currentUserIdForSimulators`). Caso o usuário não estivesse logado (o que é o cenário esperado para essas telas públicas), a função abortava a busca retornando imediatamente `setSimulators([])`, resultando em formulários vazios e com funcionalidade bloqueada.

## Arquivos Alterados
- `src/context/AppContext.tsx`: 
  - A dependência `[currentUserIdForSimulators]` foi substituída por `[]`.
  - A checagem `if (!currentUserIdForSimulators) { setSimulators([]); return; }` foi removida da inicialização da subscrição aos simuladores, o que permite o carregamento da lista de simuladores publicamente para qualquer visitante, inclusive de forma anônima.
  - Como a coleção `/simulators` já tinha a regra `allow read: if true;` no arquivo `firestore.rules`, essa alteração do React resolve o problema sem incorrer em erros de `permission-denied` por parte do Firebase. Apenas a função de criação/escrita no listener (que acontece como fallback se os simuladores estiverem vazios) recebeu um bloco `try/catch` para ser silenciada (apenas emitir log) se o visitante anônimo não tiver permissões.

## Testes Realizados
- **Cadastro Empresa (sem login):** O formulário agora consegue acessar o array `simulators` corretamente do contexto. A caixa de seleção é preenchida normalmente com a lista disponível e não fica vazia nem bloqueada.
- **Inscrição Motorista (sem login ou login novo):** O simulador agora pode ser selecionado. A escolha do simulador filtra de forma coerente a lista de empresas (mostrando apenas aquelas cujas correspondências do `simulatorId` dão 'match'), permitindo finalmente o envio da solicitação ao simulador restrito.
- **Isolamento e Segurança Geral:** Confirmei que esta mudança afeta unicamente a leitura reativa do array de simuladores e não altera `rankings`, `operações`, `histórico`, fluxo de `notificações` ou chaves de relacionamentos das entidades existentes, mantendo todos os funcionamentos da aplicação íntegros.
