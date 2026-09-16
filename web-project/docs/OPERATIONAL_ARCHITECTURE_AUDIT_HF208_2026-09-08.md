# Auditoria arquitetural operacional HF208 — achados iniciais

## Escopo

Auditoria da cadeia HF208: seleção de frete, observação nativa GTO, conclusão, submissão, progresso/job, card Web, notificações, handoff e OTA/controle remoto. Nenhuma alteração de produção foi publicada nesta fase.

## Causa 1 — card da viagem finalizada não é otimista

Em `src/pages/driver/RecordTrip.tsx`, após construir a viagem, o fluxo executa `TripsRepository.addTrip(data)`, depois `updateDoc(docRef, { tripId })`, depois `TripsRepository.syncJobProgress(activeJob.id)` e somente então libera a UI/navegação. Não existe publicação imediata da viagem recém-confirmada no estado compartilhado do histórico antes da reconciliação do listener.

Em `src/repositories/TripsRepository.ts`, `addTrip` apenas chama `addDoc`. `syncJobProgress` faz leituras de servidor de `trabalhos` e `historico_viagens`, recalcula a contagem e grava o job. A lista depende posteriormente de listener/cache, portanto a confirmação local pode ocorrer antes de o card aparecer.

## Causa 2 — conclusão Web do job é manual após `awaiting_completion`

`syncJobProgress` não define `completed`; ao atingir o total, define `status: awaiting_completion`. O `finishJob` que define `status: completed`, registra `completedAt`, cria a notificação corporativa e libera veículo/reboque está em `AppContext` e é chamado pelo `Dashboard` em `handleFinishJob`.

O `RecordTrip` não chama `finishJob` após o último `addTrip`; ele apenas navega/retorna ao perfil. Portanto, na cadeia Web atual, uma viagem pode estar registrada e o progresso em `awaiting_completion`, aguardando a ação manual de finalizar. Essa é uma causa comprovada do job não ser concluído automaticamente no caminho Web.

## Causa 3 — notificação depende da escrita/leitura remota para aparecer

`createNotification` grava primeiro em `notifications` e só usa `notificacoes` como fallback quando a coleção moderna é explicitamente negada. O `NotificationsProvider` mantém listeners Firestore e, no escopo moderno, publica o estado quando o snapshot chega. O serviço de criação não injeta o evento no `NotificationsContext`/store local.

Há deduplicação por ID derivado de `userId + dedupeKey`, mas a compatibilidade legada mantém leituras e listeners adicionais quando habilitada por padrão. O fluxo de `finishJob` primeiro atualiza o job, depois cria notificações corporativas e depois libera recursos; não há um evento local único que atualize imediatamente a central enquanto o Firestore confirma em paralelo.

## Causa 4 — handoff nativo ainda está ligado ao ACK

No `GtoObserverService`, após a conclusão local, a sessão é selada e enviada por `GtoTripSubmissionCoordinator`. O callback `automaticTripSyncListener.onSynced` só chama `beginTrip(false, false)` e prepara a próxima sessão quando o ACK atual chega e a policy permite. O Observer permanece ativo, mas o início da próxima sessão depende do ACK da viagem anterior no caminho atual.

`GtoAutoTripSync` possui outbox durável, tokens de tentativa e proteção contra callbacks atrasados, mas o boundary operacional para `beginTrip` continua no ACK. Isso pode deixar a próxima operação aguardando servidor mesmo com a conclusão local já persistida.

## Causa 5 — backend de `registerGtoTrip` não está presente nesta base

A base local contém o chamador nativo `registerGtoTrip` e os contratos de resposta `tripId`, `sessionId`, `contractVersion`, `progress` e `jobStatus`, mas não contém o código de Cloud Functions/backend que implementa a função. Não é possível comprovar nesta auditoria se o backend conclui o job, libera veículo/reboque ou apenas registra a viagem. Qualquer correção server-side exigiria o repositório/backend correspondente.

## OTA e controle remoto

A busca nativa não encontrou listeners Firestore que executem comandos operacionais remotos. O nativo chama Firebase Functions para sincronizar estado/registrar viagem e mantém a observação GTO local. A camada Web possui `OtaManager`, que valida assinatura, checksum, canal, versionCode e runtime e chama `LiveUpdate.downloadBundle`/`setNextBundle`. Essa OTA pode alterar o bundle Web; a auditoria ainda precisa decidir, com base no requisito do anexo, se a remoção do OTA do APK é aceitável ou se o contrato deve ser restringido para que a automação operacional continue exclusivamente nativa.

## Próximos pontos de implementação

1. Criar um SSOT local de operação/viagem e inserir a viagem confirmada imediatamente no dataset compartilhado antes da leitura Firestore.
2. Consolidar `addTrip`, progresso, conclusão, liberação e notificação em um coordenador idempotente; no caminho Web, confirmar se a conclusão automática pode chamar `finishJob` sem quebrar a regra de confirmação final.
3. Fazer o NotificationsProvider aceitar publicação local deduplicada e reconciliação Firestore sem esperar um novo snapshot.
4. Separar no nativo o handoff da viagem anterior do ACK, mantendo o outbox e a confirmação independente.
5. Não alterar backend ou remover OTA nativo sem o código/contrato necessário e testes de compatibilidade.

## Estado

A auditoria identificou causas reais, mas a correção integral ainda não foi aplicada. Nenhum APK, Dist ou OTA foi gerado/publicado como resultado desta auditoria.
