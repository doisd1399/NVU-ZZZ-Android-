# NVU R3.34-PC-HF18

- Android: **1.0.70 / versionCode 70**
- Base: HF17

## HF18 — integridade da seleção e leitura independente por campo

A HF18 corrige as inconsistências confirmadas da HF17 sem reescrever o fluxo estável. A origem passa a ser extraída da ROI completa da linha já selecionada, inclusive quando o ML Kit quebra `origem` e `empresaDestino` em linhas diferentes ou perde o separador `>`.

A confirmação do frete deixa de depender de consenso global: cada campo operacional (carga, origem, destino, distância e valor) possui evidência própria. `destinationCompany` permanece somente como metadado opcional e nunca bloqueia revisão, lock ou envio. O código não fabrica mais `votes=2` após revisão manual.

A identidade da linha passa por `TOUCH_LOCKED` e só vira `CONFIRMED` após uma transição visual compatível. Quando já confirmada, timeout de OCR preserva a linha e pede apenas o campo ausente. Ambas as rotas de confirmação usam a mesma transição para `TRIP_IN_PROGRESS` e exibem **“Frete identificado. Tudo preparado, podemos partir!”**.

O estado transitório de revisão/seleção é removido integralmente ao limpar uma viagem, evitando resíduos entre cancelamento, reseleção e múltiplas entregas.
