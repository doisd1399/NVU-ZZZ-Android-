# NVU R3.34-PC-HF62 — Bootstrap Coordinate Safe

Data: 2026-08-19  
Android: 1.0.114 / versionCode 114  
Base: HF61 Completion Commit Safe

## Causa raiz

O bootstrap preservava o primeiro toque rápido em Aceitar, mas `queueFreightTouchMarker` chamava a promoção por uma sobrecarga que dependia das coordenadas guardadas em campos globais e, no fallback, chamava o armamento sem os argumentos explícitos. O contrato R3.24 exige que o mesmo evento humano seja propagado explicitamente e armado uma única vez com `rawX/rawY/localX/localY` reais.

## Correção

- A promoção do candidato de lista recebe explicitamente `rawX, rawY, localX, localY`.
- Quando a promoção já armou o toque, `touchArmedDuringPromotion` impede um segundo armamento.
- Se a promoção não armou, o fallback usa exatamente as mesmas coordenadas reais.
- Divergência entre coordenada tocada e linha pressionada continua fail-closed.
- A certificação semântica da lista, o isolamento da viagem concluída HF61 e a fila idempotente permanecem intactos.

## Identidade

- functionalRelease: R3.34-PC-HF62
- Android versionName: 1.0.114
- Android versionCode: 114
- Workflow/artifact: PC-HF62

## Gates executados após a correção

- R3.24 bootstrap/origin: 14/14 PASS
- R3.3 freight re-arm: 21/21 PASS
- HF40 cancelled-list bootstrap: 14/14 PASS
- HF45 critical flow: 18/18 PASS
- HF49 automatic result: 17/17 PASS
- HF57 instant messages: 20/20 PASS
- HF58 Cost Safe: 21/21 PASS
- HF59 Sync Safe: 29/29 PASS
- HF60 Terminal Safe: 29/29 PASS
- HF61 Completion Commit: 29/29 PASS
- HF62 Bootstrap Coordinate: 10/10 PASS
- Android Java syntax: 53 sources PASS
- Native flow: 49/49 PASS
- Auto-sync: 74/74 PASS
- Firebase cost audit: 0 critical / 0 warnings
- Money integrity: 10/10 PASS
- R3.34 PC hotfix: 24/24 PASS

O comando monolítico `verify:release` foi exercitado, mas excedeu o limite de execução deste ambiente devido ao grande encadeamento de testes. Os gates críticos posteriores foram executados separadamente e estão listados acima.

## Firebase Functions

As fontes de backend não foram alteradas pela HF62.

- gtoTrips.ts SHA-256: `e11110e248fe886c0a8eb1644bb1b129b618919413ae15f8b576a56d907bd707`
- gtoState.ts SHA-256: `f15301283ea37774deef756498a655a0018be17474254dcfc09485efdcfbc836`

`functions/lib` foi emitido localmente pelo TypeScript para permitir os gates que inspecionam JavaScript compilado. Neste ambiente faltam os typings locais de Node para um build TypeScript limpo completo; o workflow oficial executa `npm --prefix functions ci` antes de `npm --prefix functions run build`.
