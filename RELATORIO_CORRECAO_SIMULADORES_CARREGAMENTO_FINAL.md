# Correção fluxo simuladores NVU

Causa raiz:
- AppContext não expunha estado de carregamento real.
- RecruitmentApply estava tratando simuladores como strings, mas a fonte Firebase retorna objetos.
- Formulários podiam renderizar antes do carregamento.

Arquivos:
- src/context/AppContext.tsx
- src/pages/RegisterCompany.tsx
- src/pages/RecruitmentApply.tsx

Correções:
- criado simulatorsLoading.
- simulators sempre retorna array.
- formulários aguardam carregamento.
- opções usam sim.id e sim.name.
- compatibilidade active !== false mantida.

Não alterado:
- simulatorId
- ranking
- histórico
- operações
