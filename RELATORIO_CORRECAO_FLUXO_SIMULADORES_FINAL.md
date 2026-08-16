# Correção final do fluxo de simuladores

## Causa raiz

- `RecruitmentApply` liberava a tela quando qualquer uma das listas tinha itens (`allCompanies.length > 0 || simulators.length > 0`), o que criava condição de corrida.
- Existia um `loading` local independente dos listeners reais do Firebase.
- O contexto não expunha um estado real de carregamento das empresas públicas.
- `RegisterCompany` não diferenciava carregamento de lista realmente vazia.

## Arquivos alterados

- `src/context/AppContext.tsx`
- `src/pages/RegisterCompany.tsx`
- `src/pages/RecruitmentApply.tsx`

## Correções aplicadas

- Adicionado `companiesLoading` ao AppContext e finalização segura em snapshot vazio ou erro.
- Mantidos `simulators` e `companies` sempre como arrays.
- Criado `formReady = !simulatorsLoading && !companiesLoading`.
- Removido o loading local e a liberação baseada no tamanho de arrays.
- A lista de simuladores vem exclusivamente da coleção `/simulators`.
- `allCompanies` é usado somente após a seleção do simulador para listar empresas compatíveis.
- O filtro usa `resolveSimulatorId(company, simulators) === selectedSimulatorId`.
- Simuladores sem `active` permanecem disponíveis por `active !== false`.
- Adicionados estados visuais de carregamento e listas vazias.

## Preservado

Não foram alterados `simulatorId`, ranking, histórico, operações, métricas, fotos/avatar, regras de negócio ou lógica de aprovação.

## Validação executada

- `npm run build`: **aprovado**, com `dist` gerado sem erro de compilação.
- Verificação estática: removidos o loading local, a condição `allCompanies.length > 0 || simulators.length > 0` e o uso de lista vazia artificial durante o carregamento.
- O `tsc --noEmit` global ainda encontra dependências ausentes em `nvu26/functions`, estrutura paralela já existente e fora dos três arquivos corrigidos; o build oficial da raiz foi aprovado.
