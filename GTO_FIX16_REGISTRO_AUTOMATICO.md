# FIX16 + registro automático NVU

Esta versão mantém o motor de seleção determinística do FIX16 e adiciona somente a camada de sincronização da viagem concluída.

## O que não foi alterado

- detecção visual da lista de fretes
- correlação do toque com os frames
- OCR do cartão selecionado
- overlay/botão flutuante
- MediaProjection
- detecção da tela de conclusão

## O que foi adicionado

- `GtoAutoTripSync.java`
- sessão UUID por nova viagem
- fila local durável de viagens finalizadas
- Firebase Auth nativo para identificar o motorista logado
- callable Firebase Functions `registerGtoTrip` em `us-central1`
- retry de sincronização quando a conexão volta
- proteção para aparelho compartilhado: uma fila só é enviada quando o mesmo `driverId` está autenticado
- estados de interface: sincronizando / registrado / recusado

A viagem só entra na fila depois de `completionStatus = CONFIRMED_NORMAL`. Fluxos de anúncio/bônus continuam fora do registro automático.

## Dependências Android adicionadas

- Firebase Android BoM
- `firebase-auth`
- `firebase-functions`

## Validações locais

Execute antes do APK:

```bash
npm run validate:gto-native
npm run validate:gto-auto-sync
npm run lint
npm run verify:project
```

O build APK final continua sendo gerado pelo Android Studio.
