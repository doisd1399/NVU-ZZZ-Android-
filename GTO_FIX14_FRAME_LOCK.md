# GTO · FIX14 — Frame Lock sem Accessibility

## Objetivo
Eliminar a dependência de AccessibilityService/TouchInteractionController e, ao mesmo tempo, preservar o frame curtíssimo em que o botão `Aceitar` muda visualmente quando o motorista toca rápido.

## Causa raiz tratada
As versões anteriores usavam `ImageReader.acquireLatestImage()`. Essa API descarta frames intermediários para entregar o quadro mais recente. Isso é adequado para OCR geral, mas pode eliminar justamente o único quadro do estado pressionado do botão `Aceitar`.

No FIX14, somente durante `WAITING_FREIGHT`, o consumidor usa `acquireNextImage()` e processa cada quadro em ordem com um detector visual extremamente pequeno, sem OCR. Nenhum vídeo é gravado e nenhum frame é salvo em arquivo.

## Seleção do frete
1. A faixa direita dos botões laranja é analisada diretamente do buffer RGBA do `Image`.
2. O detector identifica de 1 a 5 botões `Aceitar` sem OCR.
3. Cada botão recebe uma assinatura visual curta (6 x 4 células + proporção laranja).
4. O quadro atual é comparado ao imediatamente anterior.
5. Uma linha só vira candidata quando um único botão muda de forma isolada.
6. Também é tratado o caso em que o botão pressionado escurece tanto que desaparece temporariamente do detector laranja.
7. A seleção só é confirmada se a lista desaparecer logo depois da alteração do botão.
8. Nesse momento, o frame exato da mudança já está congelado em memória e o OCR roda apenas sobre a linha selecionada.

Não há uso de coordenada `ACTION_OUTSIDE`, AccessibilityService, injeção de toque ou automação do jogo.

## Overlay
- `FLAG_WATCH_OUTSIDE_TOUCH` removido da bolinha.
- Pequenos movimentos do dedo não fecham mais o menu.
- O limite de arraste usa `ViewConfiguration.getScaledTouchSlop()`.
- OCR, mudança de página e mudança de estado nunca fecham o menu.
- O menu só fecha por ação explícita: tocar novamente na bolinha, arrastar de verdade, iniciar/autorizar, cancelar, abrir painel ou finalizar.

## Estado antigo
Quando o processo nativo é recriado sem uma MediaProjection válida, qualquer viagem persistida é limpa e a sessão começa em `IDLE`.

Se o GTO ficar fora do primeiro plano por tempo suficiente e depois voltar, a sessão antiga também é descartada para evitar exibir `Cancelar viagem` de um trabalho anterior.

`Cancelar viagem` aparece somente em `TRIP_IN_PROGRESS`.

## Finalização
Durante a viagem não há OCR contínuo. Ao tocar em `Finalizar viagem`, a tela `Concluído` é lida. Depois de `Receber`, duas leituras consecutivas de gameplay confirmam a entrega em cerca de centenas de milissegundos. O fluxo deixou de depender de ACTION_OUTSIDE para perceber `Receber`.

Um retorno demorado/ambíguo não é aceito automaticamente como recebimento normal.

## Validações locais
- `npm run lint`: aprovado.
- `npm run verify:project`: aprovado.
- `npm run validate:gto-native`: 27/27 verificações aprovadas.
- Manifest: sem AccessibilityService e sem `BIND_ACCESSIBILITY_SERVICE`.
- Manifest/código: sem `RECORD_AUDIO`, `AudioRecord` ou `MediaRecorder`.
- Detector visual conferido contra capturas reais fornecidas: listas com 3, 4 e 5 fretes foram contadas corretamente.
- Teste sintático com `javac`: nenhuma falha de sintaxe Java detectada; erros restantes são apenas classes Android ausentes no ambiente sem SDK.

## Limite da validação
O runtime específico do Motorola/GTO só pode ser provado executando no aparelho. O FIX14 elimina as causas já confirmadas (frame descartado, AccessibilityService, ACTION_OUTSIDE e jitter do overlay), mas nenhuma implementação que dependa da renderização de outro aplicativo pode ser honestamente declarada 100% garantida sem teste no dispositivo real.
