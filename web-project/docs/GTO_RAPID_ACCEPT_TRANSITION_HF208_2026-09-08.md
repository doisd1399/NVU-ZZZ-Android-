# HF208 — ACEITAR rápido durante transição GTO

## Veredito local

A falha intermitente foi localizada no caminho nativo de `GtoObserverService.handleOutsideTouch()` e `handlePreciseTouch()`. Quando o estado ainda estava em transição, a lista visual não estava confirmada ou os rows ainda não estavam disponíveis, o código retornava silenciosamente antes de registrar o ACEITAR. O segundo toque funcionava porque, nesse intervalo, a lista/captura já havia sido atualizada.

A sequência era: toque físico → `ACTION_OUTSIDE`/callback preciso → `isCurrentGtoActionContext()` falso ou rows vazios → `return` sem registro → nenhuma `SelectionTransaction` → nenhum início de sessão. A corrida era entre a atualização da lista/Projection e o callback do toque; o estado auxiliar venceu e descartou o comando humano.

## Correção

Foi criado um pequeno event buffer durável, não temporizado, com `acceptEventId`, timestamp, coordenadas, estado atual, estado de Projection e estado da transação. O primeiro toque agora é registrado imediatamente como `ACCEPT_RECEIVED`. Se a geometria ainda não estiver pronta, fica como `pendingAcceptEventId` com motivo explícito, e é consumido assim que a lista atual fornecer uma linha única e verificável.

O consumo exige correlação segura por row/card; não usa nearest-row nem assume um frete quando há ambiguidade. A sessão é criada uma única vez pelo helper idempotente `startNextTripSessionOnAccept()`. ACEITAR + KM do mesmo row valida a evidência depois do início. KM divergente, card diferente, callback antigo e toque duplicado não substituem a transação ativa.

Não foi introduzido `sleep`, `postDelayed`, retry temporizado, debounce adicional, espera de frame/OCR/Firebase/ACK ou bloqueio silencioso como solução. MediaProjection, Observer e sincronização permanecem auxiliares ao comando humano.

## Testes

| Teste | Resultado |
| --- | --- |
| `gto-rapid-accept-during-transition` | PASS |
| 0/10/25/50/100/200 ms | PASS no harness determinístico |
| UI update/callback/handoff/ACK/list update | PASS no harness |
| Duplo toque | PASS — uma sessão |
| Múltiplos cards | PASS — row original preservado |
| Sem lista/frame/OCR | PASS no harness |
| Firebase offline | PASS no harness |
| ACK atrasado | PASS — não altera Trip 2 |
| `gto-first-accept-starts-next-trip` | PASS |
| `gto-next-trip-handoff` | PASS |
| `gto-first-next-freight` | PASS — 8/8 |
| TypeScript/lint | PASS |
| `verify:release` | PASS |
| Compilação Java | PASS |

## APK

- Package: `com.nvu.operacional`
- VersionCode: `286`
- VersionName: `1.0.286`
- Variante: Release, não debuggable
- Web embutido: `2.3.57`
- Runtime: `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR`
- SHA-256: `fb203839ead34e4f3b68427b85297650f78087adb112605df04a88c01f0e06b4`
- Assinatura JAR: verificada

## Limitação

O harness e a compilação não substituem o teste físico no Motorola. É obrigatório testar o primeiro ACEITAR imediatamente após concluir a Trip 1, em 0–200 ms, durante atualização visual, sem rede, com OCR ocupado, com ACK atrasado, com dois toques e com múltiplos cards. Nenhum deploy Web/OTA foi executado nesta etapa.
