# NVU GTO FIX18 R2 — Ciclo de viagem e sincronização

Data da correção: 2026-08-11

## Objetivo

Esta revisão corrige inconsistências encontradas na auditoria da R1 sem alterar o algoritmo estabilizado de detecção rápida de fretes/OCR.

## Correções aplicadas

1. Viagem ativa não é mais apagada só porque o motorista saiu temporariamente do GTO ou abriu o Painel Operacional.
2. Sessões ativas possuem snapshot persistente e podem ser recuperadas após recriação do processo Android, com TTL de segurança de 12 horas.
3. Se o processo morrer durante CONFIRMING_FREIGHT, a sessão retorna para WAITING_FREIGHT em vez de ficar presa em uma confirmação cujo frame já não existe.
4. MediaProjection é tratada separadamente da sessão: a viagem permanece preservada e a leitura de tela pode ser reautorizada.
5. Uma entrega já concluída e ainda sem ACK do Firebase bloqueia o início de outra viagem e permanece na fila durável.
6. O ACK de registerGtoTrip passa a persistir progresso e status da operação; operação concluída bloqueia nova viagem.
7. Alias `Global Truck` foi alinhado com `GTO` e `Global Truck Online` no backend FIX18.
8. Operações `delayed` passam a ser reconhecidas de forma consistente no fluxo de lançamento.
9. Falhas ao abrir/atualizar o menu flutuante e o chip de status deixam diagnóstico persistente em vez de serem silenciosas.
10. Logout Android limpa sessão GTO e autenticação Firebase nativa, evitando herança de sessão em aparelho compartilhado. Entregas já concluídas permanecem na fila, protegidas pelo UID do motorista.
11. A fila automática é reprocessada ao abrir a NVU mesmo que o observador esteja temporariamente desativado, desde que o motorista correto esteja autenticado.
12. versionCode avançado para 18 e versionName para 1.0.18.
13. `android/local.properties` foi removido do pacote por ser específico da máquina de desenvolvimento.
14. `server.cjs` e `server.cjs.map` foram removidos dos assets do APK; o script de sync também os remove em builds futuros.
15. Bundle Android local foi atualizado para refletir `delayed`, contexto de progresso/status e logout nativo. O bundle foi verificado com `node --check`.

## Preservado sem alteração funcional

- GtoFastVisualDetector
- GtoSelectionCoordinator
- correlação temporal do toque
- frame lock / histórico retrospectivo
- OCR de carga/origem/destino/km/valor
- detecção da tela de resultado e validação de bônus

Os hashes dos componentes críticos de detecção permaneceram equivalentes à base estabilizada segundo o auditor FIX18.

## Validações finais

- validate-gto-native-flow: 45/45
- validate-gto-auto-sync: 74/74
- audit-gto-fix18: 26/26
- audit-gto-overlay-recovery: 16/16
- audit-gto-r2-lifecycle: 25/25
- TypeScript/TSX alterado: transpilação sintática OK
- Java nativo alterado: estrutura lexical/chaves validada
- bundles Android alterados: sintaxe JavaScript OK
- capacitor remoto: desativado
- chave privada de assinatura: não incluída
- node_modules: não incluído

## Observação de build/deploy

O ambiente de auditoria não possui as dependências npm do projeto instaladas e, por isso, não foi realizado um novo build completo Vite/Gradle. O bundle Android já empacotado foi corrigido e validado diretamente para que o ZIP entregue não dependa desse build para conter as correções de frontend.

A alteração em `functions/src/gtoTrips.ts` só entra no backend de produção depois do deploy de `registerGtoTrip` no Firebase. Ela não é publicada automaticamente ao gerar o APK.
