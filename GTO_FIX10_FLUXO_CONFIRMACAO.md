# GTO FIX10 — confirmação de frete e overlay estável

Correção direcionada ao caso em que, após tocar em `Aceitar`, o botão NVU ainda permanecia em `WAITING_FREIGHT`; ao abrir o menu, um quadro antigo da lista fazia o menu fechar sozinho e nenhum frete era confirmado.

## Mudanças

- Novo estado `CONFIRMING_FREIGHT` assim que a linha selecionada é identificada visualmente.
- Nesse estado, quadros atrasados da lista não podem mais fechar o menu NVU.
- A confirmação usa OCR dedicado do cartão selecionado e, caso um único quadro do OCR falhe, reutiliza somente o snapshot estabilizado da MESMA linha. Nunca usa outra linha como aproximação.
- Se o OCR dedicado não retornar um objeto parseável, a linha estável selecionada ainda pode ser confirmada se os cinco campos passarem pela validação.
- Falhas deixam uma mensagem explícita no menu e retornam para nova seleção, em vez de fechar silenciosamente.
- Detector de alteração do botão `Aceitar` ficou mais sensível e a varredura estrutural passou de 38 ms para 24 ms; snapshots da lista são atualizados a cada 50 ms.
- Após confirmação, o estado muda para `TRIP_IN_PROGRESS` e o menu exibe Carga, Empresa, Destino, Distância e Ganhos.

## Teste

1. Iniciar viagem.
2. Abrir a lista e esperar o NVU recolher para a bolinha.
3. Tocar em `Aceitar`.
4. Aguardar cerca de 0,5–1 s.
5. Abrir a bolinha NVU.
6. O menu deve permanecer aberto e mostrar `Viagem em andamento` com os dados do frete.
