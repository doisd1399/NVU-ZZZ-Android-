# Fluxo automático GTO — R3.16

```text
WEB / NETLIFY
  ↓
launchGtoWork(context)
  ↓
setContext()
  ↓
validar operação aberta
  ↓
observador Android saudável
  ↓
permissões Overlay + Usage Access
  ↓
MediaProjection consent
  ↓
projectionActive
  ↓
abrir GTO
  ↓
CAPTURA
  ↓
lista de fretes
  ↓
WAITING_FREIGHT
  ↓
toque Aceitar
  ↓
frame sequence / pre-touch snapshot
  ↓
CONFIRMING_FREIGHT
  ↓
OCR preciso da mesma linha
  ↓
validação Cargo + Empresa + Destino + KM + Valor
  ↓
lockSelectedFreight()
  ↓
TRIP_IN_PROGRESS
  ↓
visual result gate
  ↓
OCR de resultado quando necessário
  ↓
RESULT_DETECTED / AWAITING_BONUS
  ↓
Receber normal → conclusão durável
Bônus/anúncio → bloqueio
  ↓
RESULT_CONFIRMED
  ↓
enqueueConfirmedTrip()
  ↓
payload selado + SHA-256
  ↓
Firebase registerGtoTrip
  ↓
ACK idempotente
  ↓
STATUS_SYNCED
```

## Retry de seleção

```text
CONFIRMING_FREIGHT
  ↓
lista fecha
  ↓
OCR/confirmação falha
  ↓
WAITING_FREIGHT
  + freightListReopenPending=true
  ↓
lista reabre
  ↓
novo gtoTripSessionId
  ↓
novo snapshot
  ↓
novo OCR generation
  ↓
WAITING_FREIGHT limpo
```

## Regra MediaProjection

```text
PERMISSION_TRANSITION
  ↓
não interpreta ausência da lista
não cria viagem
não encerra viagem
não limpa sessão
  ↓
PROJECTION_ACTIVE
  ↓
retoma análise
```
