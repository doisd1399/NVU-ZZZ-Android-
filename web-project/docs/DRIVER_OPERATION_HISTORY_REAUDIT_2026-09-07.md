# Reauditoria — carregamento imediato de Viagens da operação

## Causa raiz comprovada

O carregamento imediato anterior estava incompleto. O `Dashboard` já recebia `historicoTrips` por `useDriverTrips` ou por `tripHistoryOverride`, mas a seção visual `Viagens da operação` montava `<TripHistory embeddedJob={myJob} ... />` sem repassar esse snapshot.

O `TripHistory`, por sua vez, sempre executava `useTripHistory(activeCompanyId)`. Assim, a primeira renderização do Dashboard podia ter viagens disponíveis em memória, mas o componente da seção iniciava um segundo caminho de estado, aguardava seu próprio listener/cache por empresa e apresentava skeleton quando `finalTrips` ainda estava vazio.

Essa duplicação também criava uma race desnecessária entre o estado já disponível no Dashboard e a nova assinatura interna.

## Correção arquitetural

O `TripHistory` passa a aceitar um snapshot opcional de viagens e um estado de sincronização opcional. Quando o snapshot é fornecido, o hook interno por empresa é desabilitado para a instância visual e o componente usa imediatamente o snapshot recebido. A sincronização continua pertencendo ao proprietário do estado — o Dashboard/useDriverTrips — e atualiza o componente por props.

Quando o snapshot não é fornecido, o comportamento existente de `useTripHistory(activeCompanyId)` permanece inalterado para Histórico, páginas administrativas e demais usos.

A regra visual continua sendo `loading && finalTrips.length === 0`: skeleton somente sem conteúdo disponível. Quando há viagens, o refresh não apaga a lista nem mostra skeleton.

## Escopo preservado

Não foram alterados filtros, ordenação, identificação da operação, estrutura de `historico_viagens`, regras Firestore, contabilização, moderação, novas viagens ou outros perfis. O ajuste elimina somente a segunda fonte de estado no caminho integrado do Dashboard.
