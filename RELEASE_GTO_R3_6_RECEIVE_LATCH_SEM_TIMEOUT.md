# NVU GTO R3.6 — Receive latch sem timeout

Versão Android: `1.0.26` / `versionCode 26`.

## Regra de conclusão

A conclusão normal não depende mais de uma janela temporal após a tela de resultado.

1. A NVU detecta a tela `Concluído` e preserva o valor final.
2. Enquanto a tela de resultado estiver ativa, o motorista pode permanecer nela pelo tempo que quiser.
3. Quando o Android fornece as coordenadas do evento, a NVU distingue `Receber` de `Dobrar valor (ADS)` pela geometria real detectada na tela.
4. Toque confirmado em `Receber` é persistido (`resultAction=RECEIVE`, `resultReceiveLatched=true`) antes da confirmação da viagem e chama imediatamente a conclusão normal/queue do Firebase.
5. Não existe mais `RESULT_ACTION_CONFIRM_WINDOW_MS`.
6. Loading, tela preta, logo da Star Games, troca temporária de app e tempo decorrido não invalidam um `Receber` já confirmado.
7. Em OEMs que ocultam coordenadas de `ACTION_OUTSIDE`, a ação fica `TOUCH_PENDING` sem expirar; ADS explícito continua tendo prioridade e a transição posterior resolve o recebimento sem limite temporal.
8. Um retorno ao HUD sem qualquer ação observada na tela de resultado não confirma mais a viagem apenas por tempo.
9. Se não houve ação de resultado e a lista de fretes retorna de forma estável, a sessão incompleta anterior pode ser substituída por uma nova, como previsto no fluxo R3.5.

## Proteções adicionais

- `ADS` nunca define o latch de recebimento normal.
- Coordenadas ambíguas entre os dois botões falham de forma fechada e não registram a viagem automaticamente.
- O valor detectado é recuperado do snapshot local se o processo reiniciar.
- Um callback OCR atrasado não pode rebaixar uma viagem já `RESULT_CONFIRMED` para `RESULT_SCREEN`.
- Um latch exato em `Receber` sobrevive à recriação do serviço/processo e retoma a confirmação automaticamente.
- O watchdog de 25 s da chamada Firebase permanece; timeout de rede muda a fila de `SYNCING` para `PENDING`, sem perder a entrega.

## Componentes preservados

- `GtoFastVisualDetector.java`: SHA-256 idêntico ao R3.5.
- `GtoSelectionCoordinator.java`: SHA-256 idêntico ao R3.5.
- `functions/src/gtoTrips.ts`: SHA-256 idêntico ao R3.5.

Portanto a R3.6 não altera o detector de fretes nem o contrato Firebase/backend; a mudança é concentrada no tratamento da ação da tela de resultado.

## Validação

Suites executadas: 336 verificações de alto nível, todas aprovadas.

- 47/47 fluxo nativo GTO
- 74/74 auto-sync Android/Firebase
- 26/26 auditoria FIX18
- 25/25 ciclo de vida R2
- 25/25 fluxo automático R3
- 21/21 rearm de frete R3.3
- 42/42 compatibilidade de dispositivo/layout R3.4
- 34/34 recuperação de resultado/sync R3.5+
- 32/32 Receive latch R3.6
- 10/10 navegação Painel Sênior

O build Gradle completo deve ser executado no Android Studio do ambiente de release antes da distribuição do APK assinado.
