# HF208 — Projeto de refatoração SSOT da seleção de fretes

## Objetivo

Unificar em uma única transação a lista detectada, a mensagem de contagem, o toque físico, o row selecionado, os dados de km/valor, a escolha de origem/destino e a conclusão da viagem. O estado `TRIP_STARTED` não poderá existir como estado intermediário órfão.

## Contrato operacional

A sequência canônica será:

```text
LIST_DETECTED
→ PREVIOUS_CONTEXT_CLOSED
→ WAITING_SELECTION
→ TOUCH_RECEIVED
→ ROW_LOCKED
→ VALIDATING_SELECTED_ROW
→ ROUTE_SELECTION_REQUIRED
→ FREIGHT_COMMITTED
→ TRIP_IN_PROGRESS
→ RESULT_DETECTED
→ RESULT_CONFIRMED
→ QUEUE_SEALED
```

A mensagem `Lista de fretes detectada · N opções` é informativa, mas deve ser derivada do mesmo snapshot que alimenta `ACCEPTS: N`. A seleção só pode avançar quando um evento físico estiver vinculado à mesma geração da lista.

## Objeto autoritativo

Será criado um registro único por geração e tentativa:

```text
FreightSelectionTransaction {
  transactionId
  sessionId
  pageGeneration
  pageFingerprint
  createdAt
  updatedAt
  optionsCount
  acceptRectsSnapshot
  selectedRow
  selectedFreightSnapshot
  touchSource
  touchCoordinates
  targetLock
  validationStatus
  routeStatus
  commitStatus
}
```

Nenhum caminho de OCR, visual, `ACTION_OUTSIDE`, probe ou revisão poderá criar outro row fora desse objeto. Campos antigos em `SharedPreferences` permanecerão somente como projeções compatíveis durante a migração e serão escritos a partir do registro autoritativo.

## Regra de nova lista

A lista só se torna uma fronteira operacional quando houver uma geração estável, com pelo menos dois frames compatíveis e um fingerprint de opções diferente da sessão anterior ou uma confirmação explícita de reabertura após conclusão.

Nesse momento, o contexto operacional anterior é fechado para o seletor. O snapshot anterior não é apagado: se estiver concluído, permanece disponível para envio/retentativa; se estiver incompleto, permanece bloqueado e não será enviado. O novo ciclo recebe novo `sessionId`, novo `pageGeneration` e transação vazia.

Assim, “descartar a viagem anterior” significa removê-la da autoridade do novo ciclo, nunca apagar ou enviar uma viagem incompleta.

## Estados e invariantes

| Estado | Entrada obrigatória | Saída permitida |
|---|---|---|
| `LIST_DETECTED` | snapshot estável e `optionsCount > 0` | `WAITING_SELECTION` |
| `WAITING_SELECTION` | `pageGeneration` e `acceptRectsSnapshot` | `TOUCH_RECEIVED` |
| `TOUCH_RECEIVED` | evento físico e mesma geração | `ROW_LOCKED` |
| `ROW_LOCKED` | exatamente um row e snapshot da mesma linha | `VALIDATING_SELECTED_ROW` |
| `VALIDATING_SELECTED_ROW` | km/valor da mesma linha | `ROUTE_SELECTION_REQUIRED`, `FREIGHT_COMMITTED` ou `REVIEW_REQUIRED` |
| `ROUTE_SELECTION_REQUIRED` | origem e destino válidos da lista fechada | `FREIGHT_COMMITTED` |
| `FREIGHT_COMMITTED` | snapshot durável bloqueado | `TRIP_IN_PROGRESS` |
| `TRIP_IN_PROGRESS` | sessão com identidade válida | `RESULT_DETECTED` |
| `RESULT_CONFIRMED` | tela Concluído certificada | `QUEUE_SEALED` |

Invariantes obrigatórias:

```text
TRIP_IN_PROGRESS ⇒ sessionId + transactionId + selectedRow + targetLock + freightLocked
selectedRow ⇒ pageGeneration + acceptRectsSnapshot + selectedFreightSnapshot
selectedFreightSnapshot.distanceKm/value ⇒ selectedRow da mesma geração
ROUTE_SELECTION_REQUIRED ⇒ km/value já validados e row preservado
QUEUE_SEALED ⇒ RESULT_CONFIRMED e snapshot durável
```

Qualquer estado `TRIP_STARTED` sem `targetLock`, row, geração e snapshot válido será classificado como `ORPHANED_TRIP_STATE`, preservado para diagnóstico e normalizado para `WAITING_SELECTION` ou `REVIEW_REQUIRED`. Ele nunca será tratado como viagem em andamento.

## Políticas de toque

Com coordenadas válidas, o evento precisa atingir exatamente um `AcceptRect` do snapshot da mesma geração. Com coordenadas `0,0`, o evento físico será associado ao mesmo `touchAttemptId`; a transição posterior somente poderá resolver um row dentro do snapshot congelado. Cor, pressed-state, proximidade e OCR isolado nunca criam uma seleção.

## Dados e rota

Após `ROW_LOCKED`, km e valor são lidos exclusivamente da linha selecionada. O sistema não poderá reutilizar valores de `freightOptions` atuais, de outra página ou de um callback antigo. Depois da validação de km/valor, o seletor de origem e destino será aberto. As escolhas serão gravadas na transação, validadas contra `GtoManualRouteSelectionPolicy` e copiadas para o snapshot durável.

## Conclusão

O envio continuará bloqueado até `RESULT_CONFIRMED`. O payload final será construído exclusivamente do snapshot durável bloqueado, contendo km e valor do frete selecionado, origem/destino escolhidos e valor final da tela Concluído. Nenhum callback de nova lista poderá sobrescrever uma sessão já selada.

## Critério de aprovação

A refatoração somente será considerada apta quando passar os testes determinísticos de geração, seleção, callback atrasado, nova lista, estado órfão, rota, conclusão e outbox. A aprovação física ainda exigirá uma sequência no Motorola em que o painel mostre, sem contradição, `LIST_DETECTED`, `ROW_LOCKED`, `VALIDATING_SELECTED_ROW`, `ROUTE_SELECTION_REQUIRED`, `FREIGHT_COMMITTED` e, somente depois, `TRIP_IN_PROGRESS`.
