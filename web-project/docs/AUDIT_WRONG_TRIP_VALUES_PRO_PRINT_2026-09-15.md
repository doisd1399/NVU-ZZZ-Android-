# Auditoria focalizada — valores incorretos em viagens Pro e Print

**Data:** 15 de setembro de 2026  
**Escopo:** identificar a causa-raiz de valores incorretos persistidos em duas viagens do modo Pro e determinar se o mesmo problema pode ocorrer no modo Print.  
**Alterações de código:** nenhuma nesta auditoria.

## Conclusão executiva

A causa do modo Pro está confirmada no caminho nativo Android, antes do fallback Web. O `SimpleAutomationService` salva o OCR bruto em `receiptText` e chama `SimpleProNativeSubmissionCoordinator.submit` imediatamente. O coordenador nativo extrai o primeiro conjunto de números encontrado após os rótulos do simulador, aceita a captura quando todos os candidatos encontrados são iguais e grava diretamente `valor` e `valorCents` em `historico_viagens`.

Esse parser nativo é mais fraco que o adaptador Web já existente. Ele não exige consenso entre leituras independentes, não aplica limite/plausibilidade de valor, não compara o resultado com um crop numérico, não rejeita uma separação decimal ambígua e não passa pelo `validateProReceiptEvidence` do Web quando a gravação nativa é bem-sucedida. Assim, um OCR que acrescente/remova ponto, vírgula, zero ou grupo de milhares pode transformar uma leitura incorreta em um valor durável, mesmo que o caminho Web rejeitasse ou colocasse a captura em revisão.

O armazenamento não é a causa: `TripsRepository.addTripWithinOperationLimit` apenas grava o payload; `SimpleProNativeSubmissionCoordinator.writeTrip` e `RecordTrip` são os pontos que materializam o valor. As regras Firestore tornam a proveniência do valor imutável para alterações posteriores, portanto a correção deve ocorrer antes da primeira gravação, no parser/validador.

## Evidências confirmadas no modo Pro

| Etapa | Evidência no código | Consequência |
|---|---|---|
| Captura | `SimpleAutomationService.finishCapture` grava o texto OCR bruto em `receiptText` e chama `submitNativeReceiptIfPending` | O primeiro valor processado é o OCR nativo da captura atual |
| Autoridade | `SimpleAutomationService` chama o coordenador nativo antes de emitir o fallback Web | O caminho nativo é o primeiro dono da gravação |
| Extração | `SimpleProNativeSubmissionCoordinator.parseReceipt` varre até 45 caracteres após cada label e coleta tokens com `AMOUNT_PATTERN` | Qualquer token monetário próximo pode virar candidato |
| Consenso | O parser só rejeita quando encontra mais de um valor distinto; um único candidato, ou vários candidatos iguais, é aceito | OCR incorreto isolado pode ser aceito |
| Conversão | `parseCents` remove todos os pontos e troca vírgula por ponto antes de multiplicar por 100 | Formatos ambíguos com ponto decimal podem deslocar casas decimais/grupos |
| Persistência | `writeTrip` grava `receipt.amountCents / 100.0` em `valor` e `receipt.amountCents` em `valorCents` | O valor errado torna-se durável imediatamente |
| Fallback | O Web `validateProReceiptEvidence` só é acionado quando o nativo falha | O Web mais rigoroso não corrige uma gravação nativa já aceita |

A diferença de proteção é comprovada pelo teste Web `scripts/test-pro-receipt-adapters.ts`: ele rejeita um GTO com candidatos conflitantes (`R$ 5.300,00` e `R$ 5,30`). Essa proteção não é suficiente para o caminho nativo, porque o APK pode aceitar uma leitura OCR única ou um conjunto incompleto de candidatos antes de chegar ao adaptador Web.

## Por que os dois registros podem ter ficado errados

O padrão observado é compatível com uma falha de interpretação da evidência, não com uma alteração posterior do Firestore. O parser nativo pode ter recebido, por exemplo, uma representação OCR com separadores ou grupos numéricos incorretos e a converteu deterministically para um número válido, porém errado. Como não há faixa de plausibilidade, segunda leitura concordante nem comparação com a região numérica principal, o valor passa como `amountCents > 0` e é gravado.

A proteção de duplicidade existente impede repetir o mesmo valor consecutivamente, mas não determina se o valor reconhecido é semanticamente correto. Portanto, ela não evita dois valores diferentes, ambos incorretos.

Também foi descartada como causa primária a mistura de rota/empresa/operação entre viagens: antes do consentimento, o serviço congela `captureAttemptId`, `captureContextEpoch`, simulador, rota, empresa, job e contrato em campos `capture*`; o coordenador valida esses campos contra o contexto vivo antes de escrever. Essa parte reduz o risco de associar a viagem ao contexto errado, mas não corrige OCR monetário incorreto.

## O mesmo pode ocorrer no modo Print?

**Sim, o risco existe, mas por outro mecanismo.** No GTO Print, `analyzeGtoTripReceipt` faz primeiro OCR do crop numérico, mas depois executa OCR amplo de diagnóstico e chama novamente `promoteValue(extractValueFromText(normalizedResultText))`. A função `promoteValue` substitui o valor anterior quando o novo candidato é diferente; não há guarda que preserve o primeiro valor validado nem consenso entre os dois resultados. O comentário do código diz que o OCR amplo não deveria apagar o valor, mas a implementação atual permite essa substituição.

Depois, `RecordTrip` grava `parseTripValue(valor)` no payload. Esse normalizador trata corretamente vários formatos brasileiros, mas não consegue recuperar a intenção se o OCR amplo já entregou um token errado. Portanto, GTO Print pode sofrer valor incorreto por sobrescrita do primeiro candidato.

Os demais parsers Print também não têm o mesmo risco exato, mas apresentam superfície residual: TOE3 usa uma leitura numérica única do crop e SCS/ATS usa uma leitura única do crop de valor. Sem múltiplas leituras concordantes, um OCR ruim ainda pode passar se o token parecer válido.

| Fluxo | Risco confirmado | Mecanismo |
|---|---:|---|
| Pro nativo | **Alto e confirmado** | Parser nativo mais permissivo grava antes do Web; sem consenso/plausibilidade |
| GTO Print | **Real e confirmado por código** | OCR amplo pode substituir o valor do crop numérico |
| TOE3 Print | **Residual** | Leitura numérica única, sem consenso de frames |
| SCS/ATS Print | **Residual** | Leitura única do crop de valor; rota/dados auxiliares têm mais validações |
| Persistência Firestore | **Não é a causa** | Grava o payload recebido; regras protegem alterações posteriores do valor |

## Correção segura recomendada

A correção não deve ser feita relaxando regras ou alterando o valor depois da gravação. O caminho seguro é unificar a política de evidência antes do primeiro write:

1. Fazer o coordenador nativo usar o mesmo contrato de candidatos do adaptador Web, incluindo rejeição de valores conflitantes, separador ambíguo, ausência de label, marcador insuficiente e faixa de plausibilidade configurável por simulador.
2. Exigir duas leituras concordantes — preferencialmente crop numérico e leitura ampla contextual, ou dois frames independentes — antes de permitir a gravação nativa. Divergência deve gerar `WEB_FALLBACK`/revisão, nunca escolher silenciosamente.
3. Corrigir `promoteValue` do GTO Print para não substituir um valor já promovido pelo crop numérico, salvo se houver consenso explícito e igual. O OCR amplo deve fornecer contexto e evidência de bônus, não autoridade para trocar o valor.
4. Preservar `valorCents` como campo canônico e gravar `valor` somente como projeção de `valorCents / 100`, mantendo ambos consistentes.
5. Acrescentar testes de regressão para ponto decimal, separador brasileiro, zero extra, grupo de milhares, candidatos conflitantes, OCR amplo divergente e valor único fora de faixa.
6. Revalidar Print e Pro com o mesmo conjunto de fixtures antes de gerar APK/OTA. Nesta auditoria, **nenhum código foi alterado e nenhum APK foi gerado**.

## Veredito

A causa do erro no modo Pro está comprovada no parser nativo de valores e na prioridade do write nativo sobre o Web. O modo Print também pode apresentar valor incorreto: no GTO há uma sobrescrita confirmada pelo OCR amplo; TOE3 e SCS/ATS têm risco residual por leitura única. A correção deve ser aplicada antes da persistência e acompanhada de consenso de evidência, sem tentar reparar valores depois de gravados.
