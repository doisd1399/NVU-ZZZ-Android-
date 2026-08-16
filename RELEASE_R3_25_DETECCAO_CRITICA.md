# NVU R3.25 — correção crítica da detecção de fretes

## Causa raiz reproduzida

A captura real fornecida pelo motorista foi colocada na suíte como fixture de regressão.
O detector de produção reconhece nela **5 fretes**. Portanto, o problema observado na
R3.24 não era simplesmente cor/posição do botão `Aceitar`.

A falha principal estava no gate anterior à análise: depois da autorização de
MediaProjection, alguns aparelhos/OEMs podem não emitir um evento novo e confiável de
`UsageStats` informando que o GTO voltou ao primeiro plano. A imagem da lista já estava
chegando pela projeção, mas o serviço permanecia em `WAITING_GTO_FOREGROUND`; com isso,
os frames eram consumidos apenas pelo gate de estabilidade e **não chegavam ao fluxo de
detecção de fretes**.

## Correções R3.25

- O pixel real da lista GTO passa a ser evidência direta no estado restrito
  `WAITING_FREIGHT + MediaProjection ativa`; `UsageStats` deixa de ser hard gate.
- A liberação continua fail-closed: o detector exige geometria válida de 1..6 linhas e o
  gate exige frames estáveis antes da análise normal.
- O caminho rápido `WAITING_FREIGHT` voltou a registrar explicitamente os eventos de
  lista visível, lista fechada e lista reaberta.
- Reabrir a lista depois de uma tentativa reinicia somente a sessão de seleção e não
  contamina a nova viagem com snapshot/OCR da tentativa anterior.
- Em aparelhos que não entregam `ACTION_OUTSIDE`, há fallback visual conservador: uma
  única linha precisa mudar isoladamente; depois a lista precisa desaparecer; e a linha
  selecionada ainda precisa passar pelo OCR exato/independente antes do lock.
- Coordenada de toque divergente da linha visual continua sendo rejeitada.
- Nenhum nome/cidade é autocorrigido ou inventado. Divergência visível, por exemplo
  `Itapetuna` versus `Itapetona`, continua bloqueando a confirmação.
- A primeira viagem em contrato simples pode manter origem desconhecida quando o GTO
  não mostra essa origem; o sistema não inventa o campo e não rejeita o frete por isso.
- `contractMode?: unknown` foi adicionado ao contrato TypeScript das Firebase Functions,
  corrigindo o erro de build visto na R3.24.
- Mensagens de etapa foram encurtadas e falhas de captura/seleção mantêm causa específica.
- O menu é atualizado quando a contagem detectada muda.

## Mensagens principais

- Lista encontrada: `Lista de fretes detectada · N opções.`
- Seleção confirmada: `Frete identificado, tudo preparado. Podemos partir!`
- Lista reaberta: `Lista reaberta · selecione o novo frete.`
- Falha de seleção: `Frete não confirmado · <causa>`.

## Teste com a tela real reportada

Fixture: `scripts/fixtures/gto-real-freight-list-5.png`

A suíte usa o próprio `GtoFastVisualDetector` de produção e valida:

- imagem original 1536×691 → 5 fretes;
- variações reais da mesma página → 1, 2, 3, 4 e 5 fretes;
- escalas 1024×461, 1280×576, 1536×691 e 1920×864 → 5 fretes;
- seleção visual isolada da terceira linha;
- botão pressionado temporariamente ausente do mask → mesma terceira linha;
- fechamento da lista obrigatório antes de finalizar candidato visual.

## Fluxo auditado

`WAITING_FREIGHT → lista detectada → linha selecionada → CONFIRMING_FREIGHT → snapshot
imutável/fingerprint → TRIP_IN_PROGRESS → RESULT_DETECTED → Receber → RESULT_CONFIRMED
→ payload selado → fila local durável → registerGtoTrip → ACK → remoção da fila`.

A fila nunca é descartada antes do ACK do backend. Falha de rede mantém o payload
pendente para retry. Payloads pendentes da R3.23/R3.24 continuam compatíveis.

## Versão

- Release funcional: **R3.25**
- Web/source: **2.3.6**
- Android: **1.0.42**
- versionCode: **42**
