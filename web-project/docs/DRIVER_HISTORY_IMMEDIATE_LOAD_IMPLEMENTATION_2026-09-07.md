# Histórico de Viagens — implementação de carregamento imediato

## Veredito

**Implementação local validada.** A causa raiz foi corrigida sem remover loading legítimo, sem duplicar consultas e sem alterar regras de empresa, motorista, simulador, operação, viagens ou autorização.

A publicação Web/OTA pública não foi alterada nesta execução. Nenhum APK foi gerado.

## Causa raiz

O fluxo de abertura é `DriversTab` → `/admin/driver/:id` → `DriverProfileIsolated`.

`DriversTab` já mantém `useTripHistory(activeCompanyId)` ativo, portanto o cache por empresa pode estar aquecido antes do clique. Entretanto, `DriverProfileIsolated` introduzia atraso artificial:

```text
secondaryReady = false
→ requestAnimationFrame
→ secondaryReady = true
→ useTripHistory(viewedCompanyId, enabled: secondaryReady)
→ DriverPerformanceCard somente após secondaryReady && companyCatalogLoaded
```

Isso fazia o cabeçalho e a operação aparecerem antes do histórico, mesmo quando `useTripHistory` já tinha um snapshot de empresa em memória ou `sessionStorage`. O carregamento do catálogo global de empresas também bloqueava a montagem do card de perfil, apesar de não ser necessário para pintar o histórico já escopado.

`TripsRepository.listenCompanyTrips` continua aguardando a combinação autoritativa do listener canônico e das leituras legadas. Essa espera é intencional para consistência e não foi removida. Ela só ocorre depois que o cache disponível já pode ser exibido.

## Correção aplicada

Arquivo alterado: `src/pages/admin/DriverProfileIsolated.tsx`.

A correção removeu `secondaryReady`, o `requestAnimationFrame` e o uso desse estado como gate do histórico. O perfil agora chama:

```tsx
useTripHistory(viewedCompanyId, {
  enabled: Boolean(viewedCompanyId),
});
```

O `DriverPerformanceCard` agora monta assim que `viewedCompanyId` existe, sem aguardar `companyCatalogLoaded`. A carga do catálogo continua em background e permanece disponível para filtros, simulador e ranking quando chegar.

Não foi criado cache paralelo. O caminho existente continua sendo:

```text
estado/cache em memória ou sessionStorage
→ primeira pintura
→ listener Firestore canônico + aliases legados
→ reconciliação autoritativa
→ atualização silenciosa do cache
```

## Isolamento e consistência

O cache primário permanece por `companyId`. O histórico visual continua filtrado por `driverId`, empresa e simulador conforme os utilitários já existentes. A navegação continua transportando `companyId`, e a aba dedicada continua recebendo `defaultDriverId` e `companyId` explicitamente.

O listener canônico permanece filtrado por `historico_viagens.companyId`. As leituras legadas continuam sendo reconciliadas pelo repository e não foram substituídas por uma consulta ampla. Logout continua limpando caches via `onAuthTeardown`.

## Testes executados

| Teste | Resultado |
| --- | --- |
| `test:driver-history-scenarios` | PASS — 7 cenários: cache-first, ausência de cache, A→B→A, nova viagem, reabertura, empresa/simulador e Firebase lento |
| `test:driver-history-immediate-load` | PASS — sem gate artificial, cache-first, escopo e reconciliação |
| `test:profile-operation-instant-surface` | PASS — 28 verificações estruturais |
| `test:driver-trips-immediate-cache` | PASS — 6 verificações |
| `test:active-profile-resume` | PASS |
| `npm run lint` | PASS |
| `npm run verify:release` | PASS, incluindo build Web local 2.3.54 |

O build Web local gerou o bundle com `DriverProfileIsolated` sem o gate artificial. A validação é automatizada e estrutural/determinística; não substitui um teste físico no WebView do dispositivo.

## Limites desta execução

Não houve publicação Web/OTA, não houve sincronização de assets Android e não houve geração de APK Release ou Debug. A publicação da correção exige novo empacotamento OTA/Dist com identificador posterior ao bundle público atual, seguido de validação remota e aplicação no próximo restart da HF208.
