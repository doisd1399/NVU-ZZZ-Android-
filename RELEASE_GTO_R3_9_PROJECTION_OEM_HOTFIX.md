# NVU GTO R3.9 — MediaProjection/OEM hotfix

Android: `versionCode 29`, `versionName 1.0.29`.

## Problemas corrigidos

1. Em alguns aparelhos, tocar em **Iniciar viagem** sem MediaProjection ativa criava uma Activity transparente em uma task isolada. Alguns OEMs exibiam essa task como tela cinza/sem conteúdo.
2. Ao tocar em **Autorizar leitura da tela** dentro do GTO, alguns aparelhos retornavam para a NVU e o botão flutuante permanecia oculto.

## Fluxo novo

- Ao tocar em **Iniciar trabalho GTO** na NVU, a autorização de leitura da tela é solicitada **antes** de abrir o simulador.
- O GTO só é aberto depois de `projectionActive=true`.
- Se o Android encerrar MediaProjection durante o uso, a bolinha leva a NVU ao primeiro plano para renovar a autorização e, após confirmação, a NVU retorna automaticamente ao GTO.
- O fluxo de recuperação não cria mais a task transparente isolada sobre o simulador.
- Foram adicionadas tentativas limitadas extras para restaurar a bolinha em aparelhos lentos.

O contrato FIX18, detecção de frete, resultado, fila durável, idempotência e sincronização Firebase não foram alterados.
