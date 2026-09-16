# Auditoria local — finalização automática da operação

## Escopo

A maintenance não será publicada. Esta auditoria cobre apenas o fluxo de conclusão da operação nos modos Print e Pro e o handoff para o modal de resultados já existente.

## Causa raiz confirmada

1. `src/repositories/TripsRepository.ts:688-744` recalcula o progresso canônico e, quando o total é atingido, grava somente `status: "awaiting_completion"`. A própria implementação declara que a transição terminal pertence a `finishJob`.
2. `src/services/operationalCompletionCoordinator.ts:126-147` chama `finishJob` somente depois da reconciliação server-side retornar progresso igual ao total. Portanto, o coordenador pode fechar o job, mas não possui qualquer mecanismo para entregar dados ao modal.
3. `src/pages/driver/Dashboard.tsx:1199-1263` é o único proprietário atual do `OperationResultModal`. O modal só abre depois do clique manual em `handleFinishJob`.
4. `src/pages/driver/RecordTrip.tsx:928-969` usa o coordenador no modo Print, mas não possui estado/callback para abrir o modal após o último lançamento. O guard de job fechado pode redirecionar ou bloquear a página antes do usuário ver os resultados.
5. `src/components/SimpleAutomationCompletionBridge.tsx:186-214` recebe `result.operationClosed` no Pro, exibe apenas toast e navega ao Dashboard. Não há callback/evento para transferir o payload final ao proprietário do modal.
6. `src/services/simpleAutomationCompletionService.ts:338-364` chama o coordenador no Pro com reconciliação deferred; a conclusão pode ser confirmada em background, enquanto a UI já navega sem o modal.

## Correção pretendida

Manter `finishJob` como proprietário da transição terminal, criar um handoff de resultado compartilhado para o Dashboard e fazer Print/Pro publicarem o payload somente depois de confirmação de conclusão. Nenhuma regra de negócio, autenticação, Firestore, permissão ou outro modo de trabalho deve ser alterado.

## Implementação local concluída

O `finishJob` agora confirma no servidor que o documento realmente chegou a `completed` e relança falhas, evitando que uma falha de escrita seja tratada como conclusão. O modo Print publica o payload do resultado e o transporta ao Dashboard antes da troca de rota. O modo Pro mantém reconciliação deferred nas viagens intermediárias, mas aguarda a reconciliação terminal quando a viagem projetada atinge o total; após a confirmação, publica o mesmo payload. O Dashboard consome o handoff por evento e `sessionStorage` e abre o `OperationResultModal` existente, sem duplicar a UI.

## Validação local

As regressões de coordenador, terminalidade, registro rápido, evento/ACK, recuperação consecutiva, fast path pós-captura e antifraude passaram. `npm run verify:release`, `npm run prepare:cap-assets`, `npx cap sync android`, `npm run verify:cap-assets` e `./gradlew assembleRelease --no-daemon` passaram. O APK local compilado foi auditado com `applicationId` `com.nvu.operacional`, `versionCode` 358, `versionName` 1.0.358, Web 2.3.141, canal `production-358`, handoff/modal embutidos e modelo OCR único.

## Publicação

Nenhum deploy Netlify/OTA foi executado. A maintenance permanece não aprovada e o APK local é apenas artefato de validação; a validação física em Motorola permanece pendente.
