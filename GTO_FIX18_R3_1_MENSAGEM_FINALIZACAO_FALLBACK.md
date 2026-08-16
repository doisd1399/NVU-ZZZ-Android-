# GTO FIX18 R3.1 — mensagem de finalização e fallback

## Alteração visível ao motorista
Durante uma viagem normal, o painel da bolinha não exibe mais uma ação manual de finalização. A orientação passa a ser:

> Ao chegar ao destino, a NVU identificará a conclusão e registrará a viagem automaticamente.

O botão manual anterior `Verificar finalização agora` foi removido do fluxo normal.

## Contingência
A ação manual só é liberada quando o detector visual encontra indícios da janela de resultado, mas a confirmação automática por OCR falha repetidamente dentro de uma janela curta. Nesse caso a NVU informa o problema e apresenta:

> Confirmar conclusão da entrega

O botão apenas solicita uma nova verificação imediata da tela `Concluído`; ele não permite lançar dados manualmente nem ignora as validações de resultado/ADS.

Quando a conclusão automática é reconhecida, o fallback é limpo imediatamente. Ao iniciar/cancelar uma nova sessão, o estado de fallback também é removido.

## Escopo preservado
Nenhuma alteração foi feita em `GtoFastVisualDetector`, `GtoSelectionCoordinator`, `GtoAutoTripSync` ou `functions/src/gtoTrips.ts` nesta revisão.

## Versão Android
- versionCode: 20
- versionName: 1.0.20
