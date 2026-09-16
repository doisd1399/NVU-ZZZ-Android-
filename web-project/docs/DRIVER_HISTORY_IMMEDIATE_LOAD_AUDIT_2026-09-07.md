# Auditoria — carregamento imediato do Histórico de Viagens

## Causa raiz comprovada

A abertura ocorre em `src/pages/admin/fleet/DriversTab.tsx`, que navega diretamente para `/admin/driver/:id` e já mantém `useTripHistory(activeCompanyId)` ativo no contexto da lista. Portanto, em muitos casos o histórico da empresa já está no cache compartilhado por `companyId`.

O atraso é introduzido em `src/pages/admin/DriverProfileIsolated.tsx` por três gates encadeados:

1. `secondaryReady` começa como `false`.
2. Um `useEffect` só o torna `true` dentro de `window.requestAnimationFrame`, adiando deliberadamente o restante do carregamento.
3. `useTripHistory(viewedCompanyId)` é chamado com `enabled: secondaryReady`, portanto não assina nem expõe o cache antes desse frame.
4. O `DriverPerformanceCard`, responsável pela superfície inicial de desempenho/histórico associado ao motorista, só é montado quando `secondaryReady && companyCatalogLoaded`. Assim, mesmo que o histórico esteja em memória, o primeiro quadro mostra o cabeçalho/operação e um espaço vazio até o catálogo de empresas terminar.

O componente `TripHistory` da aba dedicada usa o mesmo `useTripHistory` por empresa e não inicia uma consulta diferente para o histórico; ele filtra o dataset já recebido. Isso confirma que o problema principal é ordenação/gate de renderização, não ausência de uma segunda consulta necessária.

## Fluxo atual

```text
Clique no motorista
→ rota /admin/driver/:id
→ DriverProfileIsolated monta
→ secondaryReady=false
→ useTripHistory(viewedCompanyId) desabilitado
→ requestAnimationFrame
→ secondaryReady=true
→ useTripHistory reidrata cache e cria listener
→ TripsRepository aguarda snapshot canônico autoritativo + aliases legados
→ companyCatalogLoaded
→ DriverPerformanceCard finalmente monta
→ histórico aparece
```

## Arquitetura existente reutilizável

`src/hooks/useTripHistory.ts` já possui:

- cache em memória por `companyId`;
- cache persistente em `sessionStorage` por `companyId`, limitado a 240 viagens e TTL de 10 minutos;
- hidratação síncrona do cache no estado inicial;
- listener compartilhado por empresa;
- atualização silenciosa e retry com backoff;
- limpeza no logout via `onAuthTeardown`.

`src/repositories/TripsRepository.ts` mantém um listener canônico por `companyId` e lê aliases legados uma vez por sessão. Ele deliberadamente espera a fonte canônica autoritativa e a compatibilidade legada antes de emitir o primeiro dataset completo. Essa espera protege a consistência, mas não deve impedir a pintura de um snapshot já disponível no cache.

## Isolamento

O cache primário do histórico é escopado por `companyId`. A página filtra o dataset recebido por `driverId` e contexto de empresa/simulador ao calcular perfil, totais e histórico. O listener por motorista (`useDriverTrips`) é usado no perfil para auxiliar a resolução da empresa observada; não é a fonte principal do `historicoTrips` passado à performance card.

## Correção planejada

A correção deve remover somente o atraso artificial e desacoplar a pintura do histórico do carregamento do catálogo:

- iniciar `useTripHistory(viewedCompanyId)` imediatamente quando o `viewedCompanyId` existir;
- manter `requestAnimationFrame` somente se houver necessidade real para tarefas secundárias, não para o histórico;
- montar `DriverPerformanceCard` quando o perfil/empresa observada estiver resolvido, mesmo que o catálogo global ainda esteja sincronizando, usando o snapshot já disponível;
- manter a sincronização Firestore e a confirmação de empresa/membership intactas;
- adicionar testes determinísticos para cache imediato, ausência de cache, troca A→B, nova viagem, reabertura e empresa/simulador isolados.

Nenhuma alteração foi aplicada ao código até este registro.
