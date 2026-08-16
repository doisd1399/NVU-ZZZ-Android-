# GTO Etapa 1 · FIX6 — empresa de origem e finalização

## Dados do frete
O cartão selecionado passa a ser apresentado no padrão solicitado:

- Carga
- Empresa (empresa de origem, exatamente o lado esquerdo de `origem > destino` no GTO)
- Destino (cidade exibida no cartão)
- Distância
- Ganhos ofertados

A empresa de destino continua sendo capturada internamente quando disponível, mas não é exibida no menu flutuante.

## Finalização da entrega
A tela `Concluído / Valor a receber` já possuía reconhecimento básico. O FIX6 transforma isso em um fluxo de confirmação independente das coordenadas de `ACTION_OUTSIDE`, pois alguns Androids entregam `(0,0)` para toques fora do overlay.

Fluxo:

1. OCR identifica `Concluído` + `Valor a receber` e captura o valor final.
2. Qualquer ação do motorista nessa tela inicia a verificação pós-resultado.
3. Se surgir evidência de anúncio/bônus/vídeo, a viagem é marcada como `REJECTED_BONUS`.
4. Se o GTO retornar de forma estável para a HUD normal (`km/h`, `FPS` etc.) sem evidência de anúncio/bônus, o recebimento normal é confirmado como `CONFIRMED_NORMAL`.
5. O valor final fica salvo em `finalGain` e o status em `completionStatus` para a futura integração automática com o fluxo web/Firebase.

Esta etapa continua sem gravar automaticamente a viagem no Firebase.
