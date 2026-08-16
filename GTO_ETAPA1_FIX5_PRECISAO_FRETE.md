# GTO · Etapa 1 · FIX5 — Precisão do frete selecionado

## Objetivo
Corrigir o caso observado no Motorola em que o GTO era reconhecido, a lista era lida, mas o Android entregava o `ACTION_OUTSIDE` com coordenadas `0,0`. O FIX4 então podia associar o toque à linha errada.

## Alterações desta versão

- A seleção do frete não é mais adivinhada a partir de `0,0` ou da posição do botão NVU.
- Após um toque externo, o observador abre uma janela curta de análise visual em alta frequência.
- Cada botão `Aceitar` da lista recebe uma assinatura visual antes do toque.
- O botão que muda visualmente durante o clique identifica a linha selecionada, desde que os outros botões continuem visíveis. Se não houver confiança suficiente, nenhuma linha é escolhida por aproximação.
- A contagem das linhas usa em conjunto `Aceitar`, `Km` e `R$`. Assim, se o OCR deixar de reconhecer um único `Aceitar`, a página ainda pode ser reconstruída corretamente.
- `Km` e `Ganhos` são vinculados geometricamente à mesma linha do cartão.
- O parser passa a separar: carga, empresa de origem, empresa de destino, destino, km e ganhos. Também suporta nomes de empresa quebrados em mais de uma linha.
- A origem da cidade nunca é inventada. O GTO não mostra a cidade de origem no cartão de frete observado. O módulo passa a manter `currentGtoCity` usando o destino da última viagem concluída e aprende relações empresa→cidade a partir das telas observadas. Se ainda não existir evidência para a primeira origem, o menu mostra explicitamente `cidade ainda não confirmada`.

## Teste recomendado

1. Inicie uma nova viagem pelo botão NVU.
2. Abra uma página de fretes e confirme que o diagnóstico mostra o mesmo número de cartões visíveis (3, 4 ou 5).
3. Feche o menu NVU e toque em `Aceitar` em um frete conhecido.
4. Abra o menu NVU assim que retornar ao caminhão.
5. Confira Carga, Origem, Destino, Distância e Ganhos.
6. Repita escolhendo a primeira, uma linha do meio e a última linha de páginas diferentes.

Se a confirmação visual não atingir confiança suficiente, o estado permanece aguardando em vez de registrar uma linha errada.
