# NVU R3.30 — Frete selecionado robusto

## Causa raiz confirmada

A R3.29 detectava corretamente a lista e também chegava à etapa de confirmação da linha selecionada. A mensagem real `Duas leituras da linha selecionada divergiram...` só pode ser emitida depois que:

1. uma linha foi selecionada;
2. o snapshot pré-toque foi congelado;
3. o OCR da linha selecionada retornou uma opção;
4. existe uma leitura da mesma linha na página estabilizada.

Portanto, a falha não estava em localizar o botão `Aceitar`; estava no gate posterior de comparação entre dois parses OCR.

No screenshot real da lista, a rota `Fábrica de Tijolo > Cooper Log` pode ser renderizada/quebrada em duas linhas. O parser completo da página já juntava a continuação `Log`, mas `refinePreciseRowFields()` podia sobrescrever `destinationCompany` apenas com o texto existente na linha do separador (`Cooper`). Isso transformava duas leituras da mesma imagem em `Cooper Log` versus `Cooper` e a seleção era rejeitada.

Além disso, se o motorista tocasse rapidamente, `stableFreightForRow()` podia existir com somente uma leitura; por existir, a R3.29 não forçava a segunda leitura canônica necessária antes da confirmação.

## Correção

- O refinamento do OCR recortado é agora **fill-only** e não sobrescreve um campo já parseado de forma mais completa.
- Continuação de empresa de destino em múltiplas linhas é reconstruída até a linha de cidade de destino.
- Antes de confirmar a seleção, o sistema força nova leitura da página se a linha canônica ainda não atingir `isStableFreightSafeToCommit()`.
- A identidade da linha vem da geometria exata da seleção e da geração imutável da página.
- Os dados persistidos vêm da linha canônica estabilizada (`copyFreightOption(stable)`), preservando literalmente cargo, empresas, cidade, distância e valor.
- O OCR recortado passa a ser secundário: campos ausentes ou diferenças de quebra/acentuação não reescrevem nem cancelam a linha canônica.
- Divergência numérica explícita em `Km` ou valor continua bloqueando a confirmação.
- Diferenças textuais do OCR secundário ficam registradas em `lastFreightSecondaryReadDiff` para diagnóstico, sem alterar os dados canônicos.

## Fixtures reais incorporadas

As cinco capturas enviadas no teste da R3.29 foram incorporadas à regressão da R3.30. A tela de lista real é reconhecida com 5 fretes; as quatro telas pós-seleção/erro permanecem neutras. O teste de pressão visual é exercitado nas linhas 1 a 5.

## Versões

- Release funcional: R3.30
- Android: 1.0.47 / versionCode 47
- Web: 2.3.9 (sem alteração)
- Firebase Functions: sem alteração
