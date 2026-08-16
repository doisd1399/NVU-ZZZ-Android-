# NVU GTO R3.3 — Correção de rearmamento da seleção de frete

Versão Android: `1.0.23` (`versionCode 23`)

## Causa raiz confirmada

A R3.2 não alterou o algoritmo principal que detecta o botão pressionado na lista de fretes. Os arquivos `GtoFastVisualDetector.java` e `GtoSelectionCoordinator.java` são byte a byte iguais aos da R3.1 que detectou fretes corretamente.

A falha estava na máquina de estados ao retornar para a lista de fretes enquanto a NVU ainda possuía uma sessão `TRIP_IN_PROGRESS` preservada. Para evitar cancelar uma rota válida por falso positivo, o código esperava 4 quadros e pelo menos 420 ms antes de encerrar a sessão anterior e voltar para `WAITING_FREIGHT`. Um motorista que tocasse rapidamente em `Aceitar` dentro dessa janela podia fazer o toque acontecer antes de o caminho normal de seleção estar rearmado.

## Correção R3.3

- A primeira evidência válida da lista de fretes agora pré-arma silenciosamente o sensor de toque.
- A página de fretes é congelada antes da troca de estado, preservando os dados para OCR da linha correta.
- Um único quadro permissivo + um toque qualquer não pode cancelar uma rota válida.
- Um toque fica pendente até existir uma segunda evidência de lista ou uma alteração visual específica de uma linha `Aceitar`.
- Quando a nova lista é confirmada, a sessão antiga incompleta é descartada e uma nova `sessionId` é criada antes de registrar o novo frete.
- A geometria, snapshot e sequência de quadros pré-toque são transferidos para a nova sessão, evitando perder taps rápidos.
- O caminho sem toque continua exigindo 4 quadros + 420 ms para cancelar/reiniciar, preservando a proteção contra falso positivo.
- Entregas já concluídas continuam fora desse caminho e nunca são descartadas.
- O detector principal e o coordenador de seleção continuam inalterados.
- A correção R3.2 da finalização automática/fallback sem aviso prematuro foi preservada.

## Fluxo esperado

1. Motorista inicia a viagem NVU.
2. Estado entra em `WAITING_FREIGHT` e a leitura da tela fica ativa.
3. Lista GTO aparece; a página é detectada e pré-lida.
4. Motorista toca em `Aceitar`, inclusive rapidamente.
5. A NVU correlaciona toque + quadros, congela a linha e executa OCR somente do frete selecionado.
6. Dados válidos são selados no snapshot FIX18 e o estado muda para `TRIP_IN_PROGRESS`.
7. Se a rota for cancelada/reiniciada no GTO, o retorno da lista encerra apenas a viagem incompleta e rearma imediatamente a próxima seleção.
8. Ao chegar ao destino, a tela `Concluído` é detectada automaticamente.
9. `Receber` normal confirma a conclusão; ADS continua rejeitado pelo fluxo normal.
10. O payload é preservado localmente e enviado por `registerGtoTrip` com idempotência.
11. Falha de rede mantém a viagem na fila; sucesso remove somente após ACK FIX18.
12. Operação concluída bloqueia nova viagem e orienta iniciar uma nova operação.

## Validações executadas

- `validate-gto-native-flow.mjs`: 47/47
- `validate-gto-auto-sync.mjs`: 74/74
- `audit-gto-fix18.mjs`: 26/26
- `audit-gto-r2-lifecycle.mjs`: 25/25
- `audit-gto-r3-guided-auto-flow.mjs`: 25/25
- `audit-gto-r3-2-result-fallback.mjs`: 11/11
- `audit-gto-r3-3-freight-rearm.mjs`: 21/21

Hashes preservados do detector funcional:

- `GtoFastVisualDetector.java`: `069c51986dd6bdf58e2b8d12d1fdcb9862f35c3b24f27c30613241a6fe8ecbfd`
- `GtoSelectionCoordinator.java`: `d84fe0848f5a054225cf939786156c07a291a4eb74a362ca9f72878d920b0ddd`

## Build local

O pacote não contém `node_modules`, chave `.jks` ou `local.properties`. No Windows:

```bat
npm install
npm run cap:sync:android
npm run cap:open:android
```

O `cap:sync:android` desta versão também executa automaticamente a auditoria R3.3 antes do build.

