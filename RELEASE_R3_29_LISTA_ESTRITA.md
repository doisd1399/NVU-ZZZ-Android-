# NVU R3.29 — Lista de fretes estrita

## Causa raiz confirmada

O detector R3.28 tratava qualquer pilha vertical plausível de pixels laranja no lado direito como se fossem botões `Aceitar` da lista do GTO. As capturas reais reportadas comprovaram falsos positivos em tela normal de condução/pátio e no menu de rota.

## Correção

- `FREIGHT_LIST` agora exige assinatura combinada: primeira linha no topo do painel, largura realista do botão, preenchimento laranja mínimo, alinhamento de coluna, dimensões repetidas e cadência vertical de cards.
- A segunda leitura visual usada pelo OCR aplica a mesma validação estrita.
- Telas de condução, mapa/menu, pátio e demais telas não correspondentes permanecem neutras e não iniciam OCR de lista nem alteram a etapa.
- A ausência da lista continua zerando `freightCount`/`screenState` após a janela curta de fechamento, evitando status antigo persistente.
- O watchdog de contenção do OCR preciso foi ampliado para 8 s para aparelhos mais lentos; o dado continua sendo rejeitado se não houver concordância independente.

## Regressão de pixels reais

As sete capturas do teste físico de 14/08/2026 foram incorporadas à suíte. Somente a captura da lista real é aceita (`5` fretes). As seis telas não-lista são rejeitadas. A seleção simulada nas cinco linhas da lista real continua identificando a linha exata.

## Versões

- Release funcional: R3.29
- Android: 1.0.46 / versionCode 46
- Web: 2.3.9 (sem alteração funcional Web nesta correção)
