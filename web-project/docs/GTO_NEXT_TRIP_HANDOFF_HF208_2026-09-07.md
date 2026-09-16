# HF208 — GTO Next-Trip Handoff

## Escopo

Correção nativa Android da base oficial HF208 para separar a preparação da próxima sessão GTO do ACK remoto da viagem anterior. Web/OTA não foram alterados nem publicados nesta execução.

## Causa raiz comprovada

`GtoObserverService.canPrepareNextFreightFromSealedQueue()` exigia `gtoTripSyncStatus == STATUS_SYNCED` antes de liberar `WAITING_FREIGHT`. A fila local já estava selada e protegida, mas a próxima sessão permanecia dependente da rede/Firebase. O `GtoAutoTripSync` já possuía a separação de sessões: callbacks antigos verificam `currentSession`, e somente a sessão ativa recebe `gtoTripSyncStatus=SYNCED`. O bloqueio real estava na guarda redundante do Observer.

## Implementação

`prepareNextFreightFromSealedQueue` foi centralizado como `handoffToNextTripSession`. A transição agora exige fila local selada, operação não encerrada e existência de próximo frete pela política de progresso. Ela cria novo `gtoTripSessionId`, novo snapshot, zera somente o estado visual da viagem anterior e entra em `WAITING_FREIGHT`. Não chama `stopProjection`, não destrói MediaProjection e não para o Observer.

O ACK da viagem anterior continua sendo enviado pelo outbox idempotente. Quando chegar depois do handoff, ele é registrado como ACK independente e não altera o estado de sincronização da nova sessão. Viagem sem próximo frete continua sem criar sessão fantasma.

## Validações

- Gate determinístico `gto-next-trip-handoff`: PASS — 7 cenários.
- Política Trip 1/2, Trip 2/2 e operação de uma viagem: PASS.
- Callback atrasado/duplicado e ACK independente: PASS por asserções estruturais.
- Preservação de MediaProjection/Observer no handoff: PASS por asserções estruturais.
- `:app:compileDebugJavaWithJavac`: PASS.
- `npm run verify:release`: PASS.
- APK Release: `assembleRelease` PASS.
- Package: `com.nvu.operacional`.
- VersionCode/versionName: `286` / `1.0.286`.
- Web embutido: `2.3.57`.
- Release não debuggable: confirmado.
- Assinatura compatível do release: SHA-1 `0B:43:55:B1:15:D8:03:8B:B6:F1:07:88:49:3A:71:A0:2F:80:35:C8`.
- APK SHA-256: `be1133df82d6aa4cb5ddcfe62a6848635ba2760c78c5f3436e4800421274e45b`.
- ZIP/APK íntegro e sem keystore, `google-services.json`, `.env` ou `local.properties` embutidos.

## Limites

Não houve teste físico nesta execução no Motorola Edge 50. A prova automatizada cobre a política, guards e compilação; ainda é necessário instalar no dispositivo, concluir uma viagem, confirmar `Concluído`, verificar entrada imediata na próxima `WAITING_FREIGHT`, testar com rede desativada e depois observar o ACK tardio sem perda da próxima sessão. Nenhum deploy Web/OTA foi executado.
