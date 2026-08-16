# Relatório — Correção definitiva dos seletores de simuladores

## Arquivo-base auditado

`nvu-zzz (29).zip`

## Causa raiz confirmada

A tela já renderizava o campo de seleção, portanto o listener não estava simplesmente retornando `undefined`. A falha estava na combinação de quatro inconsistências:

1. **Documentos do Firestore eram repassados sem normalização.**
   - A interface exigia `name` e `active`.
   - Documentos legados podiam usar `simulatorName`, `nome`, `label`, `title`, `displayName`, `isActive` ou `ativo`.
   - Quando o documento existia, mas não possuía `name`, o `<option>` era criado com valor real e rótulo vazio.

2. **Os formulários dependiam do seletor nativo do navegador.**
   - O seletor recebia foco no preview, mas o diálogo de opções não era confiável dentro do preview incorporado do Google AI Studio/Android WebView.
   - O problema afetava os dois formulários porque ambos usavam `<select>` nativo.

3. **O `AppContext` não incluía `simulatorsLoading` na lista de dependências do `useMemo`.**
   - Em determinados caminhos, especialmente erro sem mudança da referência do array vazio, o valor exposto pelo contexto podia permanecer desatualizado.

4. **Os arquivos de regras estavam divergentes.**
   - `firestore.rules` permitia leitura pública de `/simulators`.
   - `firestore.rules.v6` não possuía a mesma regra.
   - Dependendo do arquivo de regras publicado, as telas públicas podiam receber `permission-denied`.

## Correção aplicada

### `src/lib/simulatorCatalog.ts`

Criado normalizador central que:

- mantém o ID do documento Firestore como `simulatorId` oficial;
- resolve o nome por `name`, `simulatorName`, `nome`, `label`, `title` ou `displayName`;
- usa o próprio ID do documento como rótulo legível quando o nome estiver ausente;
- resolve atividade por `active`, `isActive` ou `ativo`;
- considera documentos antigos sem campo de atividade como ativos;
- não cria, não injeta e não mantém lista fixa de simuladores.

### `src/context/AppContext.tsx`

- O snapshot de `/simulators` agora é normalizado antes de entrar no estado.
- Adicionado `simulatorsError` para diferenciar coleção vazia de falha de permissão.
- Mantido `simulators` sempre como array.
- Mantido listener único e independente de autenticação.
- Adicionados `simulatorsLoading` e `simulatorsError` às dependências do valor do contexto.

### `src/components/ui/SafeSelect.tsx`

Criado seletor baseado no DOM, com painel modal responsivo, que:

- abre dentro do próprio preview/WebView;
- não depende do diálogo nativo de `<select>`;
- funciona com toque no Android;
- apresenta lista rolável;
- fecha ao selecionar, tocar fora ou pressionar Escape;
- rejeita opções sem valor ou rótulo.

### `src/pages/RegisterCompany.tsx`

- Substituído `<select>` nativo por `SafeSelect`.
- Mantido `simulatorId` como valor salvo.
- Mantido `simulatorName` apenas como campo de exibição/compatibilidade.
- Adicionado estado explícito para erro de leitura do Firebase.

### `src/pages/RecruitmentApply.tsx`

- Substituído o seletor nativo de simulador por `SafeSelect`.
- Substituído também o seletor nativo de empresa para evitar a mesma falha após escolher o simulador.
- Mantido filtro por `resolveSimulatorId(company, simulators) === selectedSimulatorId`.
- Mantidas regras de inscrição e aprovação.

### Regras Firestore

A regra pública de leitura de `/simulators` foi alinhada em:

- `firestore.rules`
- `firestore.rules.v6`
- espelhos dentro de `nvu26/`

## Arquivos alterados

- `src/context/AppContext.tsx`
- `src/pages/RegisterCompany.tsx`
- `src/pages/RecruitmentApply.tsx`
- `src/lib/simulatorCatalog.ts` — novo
- `src/components/ui/SafeSelect.tsx` — novo
- `firestore.rules.v6`
- arquivos equivalentes no espelho `nvu26/`

## Preservado

Não foram alterados:

- regras de `simulatorId`;
- ranking;
- histórico;
- operações;
- métricas;
- fotos/avatar;
- aprovação;
- regras de negócio.

## Validações executadas

1. **Teste unitário do normalizador**
   - documentos canônicos;
   - campos legados;
   - documento sem nome;
   - atividade legada;
   - deduplicação por ID.
   - Resultado: aprovado.

2. **Teste do fluxo simulador → empresa**
   - seleção por `simulatorId` canônico;
   - compatibilidade com `simulatorName` legado;
   - compatibilidade com `simulator` legado.
   - Resultado: aprovado.

3. **Verificação de contrato do código**
   - listener público presente;
   - sem autenticação obrigatória no listener;
   - sem seed ou lista fixa;
   - `SafeSelect` presente nos dois formulários;
   - nenhum `<select>` nativo restante nos arquivos auditados;
   - regra pública presente.
   - Resultado: 9/9 verificações aprovadas.

4. **Transpilação de sintaxe TypeScript/TSX**
   - 154 arquivos verificados, incluindo raiz e espelho `nvu26`.
   - Resultado: aprovado, sem erro de sintaxe.

5. **Resolução de imports relativos**
   - Resultado: aprovado.

## Observação de implantação

O ZIP contém as regras corrigidas. Caso o projeto Firebase real ainda esteja usando regras antigas, é necessário publicar `firestore.rules` no Firebase. A correção de interface e normalização já funciona no código; a publicação das regras é necessária somente se o Firebase retornar `permission-denied` para usuários deslogados.
