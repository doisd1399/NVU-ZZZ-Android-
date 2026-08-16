# GTO FIX18 R3 — fluxo guiado, cancelamento/reinício e finalização automática

## Objetivo

Esta revisão mantém intactos o detector rápido de fretes, o coordenador de seleção e o motor de sincronização auditado, e reforça o ciclo de vida ao redor deles.

## Fluxo apresentado ao motorista

1. **Etapa 1/4 — escolher frete**: a NVU informa que está aguardando a escolha e valida os dados automaticamente.
2. **Etapa 2/4 — realizar rota**: após bloquear o frete selecionado, a NVU informa que a rota pode ser feita normalmente e que a conclusão será detectada automaticamente.
3. **Etapa 3/4 — receber no GTO**: quando a janela `Concluído` é validada, a NVU mostra o valor detectado e orienta o motorista a usar `Receber`, sem ADS.
4. **Etapa 4/4 — envio automático**: depois do recebimento normal, a conclusão é persistida localmente, selada e enviada à NVU/Firebase automaticamente. Falha de rede mantém a entrega na fila.

O menu flutuante mostra a etapa atual permanentemente. As mensagens sobre o jogo aparecem apenas em transições de estado e desaparecem sozinhas.

## Finalização automática com baixo impacto

A R2 já continha a análise da tela de resultado, porém o caminho normal de `TRIP_IN_PROGRESS` só acordava OCR na finalização manual. A R3 corrige isso.

Durante a rota:

- um novo `GtoResultVisualGate` faz apenas amostragem de poucos pixels da região central;
- o detector procura a geometria/cor típica da janela de conclusão;
- somente quando há candidato o ML Kit OCR é acordado imediatamente;
- existe uma leitura lenta de contingência a cada ~3,2 s;
- a aceitação continua dependendo de `parseResultScreen()` ler os textos e o valor, portanto o gate visual sozinho nunca conclui uma viagem.

O `GtoFastVisualDetector` usado na seleção de fretes não foi alterado.

## Cancelamento ou reinício do GTO durante uma viagem

Enquanto `TRIP_IN_PROGRESS`, a NVU também observa de forma OCR-free a região dos botões `Aceitar`.

Se a lista real de fretes reaparecer de forma estável por 4 frames e pelo menos 420 ms:

- a sessão inacabada anterior é marcada como encerrada no GTO;
- o snapshot do frete anterior é descartado;
- nenhum ganho dessa viagem é enviado;
- uma nova `sessionId` é criada automaticamente;
- a NVU volta para a Etapa 1/4, pronta para o novo frete.

Isso cobre tanto o cancelamento da rota dentro do GTO quanto o caso de o jogo fechar/reiniciar e perder a rota, assim que o motorista voltar à lista de fretes.

Uma entrega já em `RESULT_CONFIRMED` não entra nesse caminho: se estiver concluída e apenas aguardando Firebase, permanece na fila.

## Contingências preservadas

- `Verificar finalização agora` permanece no menu, mas é explicitamente uma contingência; a finalização principal é automática.
- Perda do MediaProjection após morte do processo exige nova autorização Android, sem apagar a viagem válida.
- Troca temporária de aplicativo não cancela a viagem.
- Retorno ao GTO após ausência mostra que a NVU está verificando a continuidade.

## Compatibilidade e regressão

Arquivos sensíveis preservados sem alteração em relação à R2 auditada:

- `GtoFastVisualDetector.java`
- `GtoSelectionCoordinator.java`
- `GtoAutoTripSync.java`
- `functions/src/gtoTrips.ts`

Versão Android: `versionCode 19`, `versionName 1.0.19`.

## Validação automatizada

- Fluxo nativo GTO: **47/47**
- Contrato Android/Firebase: **74/74**
- Auditoria FIX18: **26/26**
- Auditoria de ciclo de vida R2: **25/25**
- Auditoria R3 de fluxo guiado/automático: **24/24**
- Bundle `RecordTrip` embarcado: sintaxe JavaScript validada

O build Gradle completo não pôde ser executado neste ambiente porque o Gradle Wrapper tentou baixar sua distribuição de `services.gradle.org` e o ambiente não possui acesso DNS externo. Isso não foi contabilizado como falha do projeto.
