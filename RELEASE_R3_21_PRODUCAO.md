# NVU R3.21 — release estabilizada

## Identificação

- Release funcional: R3.21
- Web: 2.3.2
- Android: 1.0.38 (versionCode 38)
- Application ID: `com.nvu.operacional`
- Runtime WebView: Netlify remoto com fallback web embarcado
- Firebase: `vtc-frota-log`

## Correção principal

A autorização inicial da MediaProjection continua na `MainActivity`, preservando
as correções de compatibilidade OEM da R3.20. Foi adicionada uma barreira
explícita antes de qualquer análise visual:

1. captura iniciada;
2. abertura solicitada do GTO;
3. evidência nova de GTO em primeiro plano;
4. orientação e proporção compatíveis com o display atual;
5. três quadros consecutivos na geometria final por pelo menos 280 ms;
6. invalidação final dos dados ligados à geometria;
7. liberação do detector com um baseline novo.

Em todo resize são invalidados baseline visual, snapshot da página, opções OCR e
correlação de toque. O sistema opera em modo fail-closed: se o redimensionamento
falhar, a projeção é encerrada e uma nova autorização é exigida.

## Contratos preservados

Não foram removidos nem reconstruídos durante resize:

- `selectedFreight` e snapshot write-once;
- `freightFingerprint`;
- estados `CONFIRMING_FREIGHT`, `TRIP_IN_PROGRESS`, `RECEIVE_LATCHED` e
  `RESULT_CONFIRMED`;
- snapshot local do resultado;
- payload selado e fila durável;
- retry/watchdog, idempotência e remoção somente após ACK;
- `registerGtoTrip`, `syncGtoTripState` e estado canônico Firestore.

Também foram preservados os componentes críticos de detecção e sincronização:
`GtoFastVisualDetector`, `GtoSelectionCoordinator`, `GtoResultVisualGate` e
`GtoAutoTripSync`.

## Validação automatizada

- Java/Android: todos os 9 arquivos Java analisados sintaticamente;
- fluxo nativo GTO: 47/47;
- AutoSync/Firebase: 74/74;
- Receive latch: 32/32;
- estabilidade histórica R3.7 atualizada: 63/63;
- modos R3.13: 20/20;
- evidência de impressão R3.13: 12/12;
- integridade da seleção R3.17: 19/19;
- ponta a ponta R3.19: 34/34;
- paridade APK/Web R3.20: 36/36;
- nova barreira de captura R3.21: 13/13;
- fallback WebView sem chunks obsoletos e idêntico ao `dist` por SHA-256;
- TypeScript Web, build Vite/Node e build das Firebase Functions: aprovados.

Use `npm run verify:release` para repetir a certificação.

## Publicação Android

O ZIP contém o projeto Android/Capacitor e os assets web sincronizados. A chave
oficial de assinatura não é incluída. Gere o APK/AAB assinado no Android Studio
com a mesma chave da versão já publicada; uma chave nova não poderá atualizar o
aplicativo existente.

O build binário e a assinatura não foram executados neste ambiente porque ele
não contém o Android SDK 36 nem a distribuição Gradle 8.14.3. Isso não é
substituído por um APK antigo: gere e assine o artefato final no Android Studio
do ambiente de publicação depois de executar `PREPARAR-ANDROID-WINDOWS.bat`.

## Dependências

O audit de produção da aplicação Web retorna zero vulnerabilidades. As Firebase
Functions permanecem na geração compatível com as funções v1 atualmente
implantadas; o NPM reporta avisos moderados em dependências transitivas e propõe
como correção automática uma migração quebradora para Admin 14 / Functions 7.
Essa migração não foi forçada dentro desta correção de captura, pois exigiria
alterar a API de todas as funções e uma homologação independente no Firebase.
