# Correção — Histórico da operação no perfil do motorista

## Veredito local

A correção foi implementada e aprovada nos testes automatizados e no `verify:release` local. Nenhum APK foi gerado e nenhuma OTA/Dist pública foi sobrescrita nesta execução.

## Causa raiz

O `Dashboard` já possuía o histórico da operação em `historicoTrips`, vindo de `useDriverTrips` ou de `tripHistoryOverride`. Entretanto, ao renderizar a seção `Viagens da operação`, ele montava `TripHistory` sem passar esse snapshot.

O `TripHistory` então executava uma segunda chamada `useTripHistory(activeCompanyId)`. Essa nova instância podia começar com estado vazio e loading próprio, fazendo a UI exibir skeleton antes de receber o mesmo histórico que já estava disponível no Dashboard.

Não era apenas um atraso de rede: era uma duplicação arquitetural do proprietário do estado.

## Implementação

`TripHistory` agora aceita `historicoTripsOverride`, `historyLoadingOverride` e `historyErrorOverride`. Quando o override existe, a instância visual usa imediatamente os dados recebidos e chama `useTripHistory(activeCompanyId, { enabled: false })`, eliminando o segundo listener nesse caminho.

O Dashboard passa seu `historicoTrips` e `tripHistoryLoading` para o componente. A sincronização continua pertencendo ao hook do Dashboard, que atualiza as props sem apagar o conteúdo anterior.

As demais telas que não fornecem override continuam usando o listener canônico por empresa sem mudança de comportamento.

A regra de loading permanece: `TripHistorySkeleton` só é renderizado quando `loading` é verdadeiro e `finalTrips.length === 0`. Se já houver viagens, refresh não remove a lista nem reexibe skeleton.

## Testes

| Cenário | Resultado |
| --- | --- |
| Gate estrutural de carregamento imediato | PASS |
| Dashboard repassa snapshot | PASS |
| Segundo listener desabilitado com override | PASS |
| Cache-first e histórico persistente | PASS |
| Motorista A → B → A | PASS |
| Empresa/simulador isolados | PASS |
| Reabertura com cache | PASS |
| Nova viagem no snapshot seguinte | PASS |
| Firebase lento sem apagar cache | PASS |
| Last Known Good State | PASS |
| TypeScript/lint | PASS |
| `verify:release` e build Web local | PASS |

## Publicação

O projeto local continua na versão Web `2.3.55`, que já é a versão pública atual do canal `production-286`. Como esta correção ainda não estava no bundle público 2.3.55, não foi correto sobrescrevê-lo com o mesmo identificador.

Para disponibilizar a correção por OTA, o próximo passo seguro é versionar a camada Web como `2.3.56`, gerar nova Dist e bundle `production-286-2.3.56`, validar assinatura/checksum e publicar separadamente. Nenhuma alteração Android é necessária para esta correção.
