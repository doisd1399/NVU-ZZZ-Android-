# R3.34 — Integridade monetária GTO

## Causa raiz

A R3.33 removia todos os caracteres não numéricos do valor final antes do consenso. Assim:

`R$ 5.300,00` → `530000` → `R$ 530000`.

O backend recebia esse valor já corrompido e o histórico o formatava corretamente como `R$ 530.000,00`.

## Correção

1. Novo `GtoMoneyValue` interpreta separadores de milhar e centavos explicitamente.
2. `GtoResultValueConsensus` armazena cada voto em centavos (`c530000` = R$ 5.300,00), eliminando ambiguidade.
3. Evidência pré-R3.34 sem unidade é ignorada após upgrade.
4. A fila local compara valor final e valor ofertado e bloqueia corrupção extrema, incluindo o padrão ~100x da falha anterior.
5. `functions/src/gtoMoney.ts` aplica a mesma interpretação e a mesma barreira no backend antes da criação de `historico_viagens`.
6. Nenhuma correção é feita dividindo valores cegamente por 100.

## Compatibilidade

São reconhecidos como equivalentes:

- `R$ 5300`
- `R$ 5.300`
- `R$ 5300,00`
- `R$ 5.300,00`
- `R$ 5300.00`

Centavos reais são preservados, por exemplo `R$ 5.300,50`.

## Registros históricos

Esta release impede novos registros errados. Registros já gravados como 100x no Firestore não são alterados automaticamente, pois uma correção histórica deve ser auditável e limitada a documentos cuja relação `valor / valorPrevisto` comprove o padrão da falha.
