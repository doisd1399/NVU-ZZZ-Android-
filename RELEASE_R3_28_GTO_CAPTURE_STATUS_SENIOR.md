# NVU R3.28 — captura GTO, detecção real, status e Painel Sênior

## Causa raiz observada no aparelho

A captura fornecida pelo motorista mostrou o estado `WAITING_FREIGHT`, porém a captura nativa
permanecia em `1220x2712` e `0/3` quadros estáveis enquanto o GTO estava em paisagem. O fluxo
R3.27-HF2 solicitava MediaProjection cedo demais, com a NVU ainda em primeiro plano/retrato.
Assim, a lista real podia estar visível no GTO sem que os quadros fossem liberados para o detector.
Sem quadro analisável não existe evento de lista detectada, seleção nem frete atual.

A própria captura real de fretes enviada no teste (1536x691) foi executada diretamente no
`GtoFastVisualDetector` de produção e é reconhecida como 5 fretes. Isso isolou a falha principal
na preparação/captura, não na geometria básica da lista.

## Correções R3.28

- GTO é aberto primeiro; a autorização MediaProjection é armada antes, mas exibida somente depois
  de o pacote real do GTO ser confirmado em primeiro plano.
- A autorização usa `GtoProjectionPermissionActivity`, transparente, paisagem, fora de Recentes,
  e pede tela inteira no Android 14+.
- A captura começa com métricas reais orientadas à rotação; se o GTO já é paisagem e a métrica
  inicial vier retrato, largura/altura são normalizadas antes de criar o ImageReader.
- Android 14+ recebe uma janela curta para `onCapturedContentResize`; OEM que não enviar callback
  recebe fallback limitado pelas métricas reais do display, evitando ficar eternamente em 0/3.
- A transição intencional `IDLE -> WAITING_FREIGHT` feita durante a preparação do lançamento não é
  mais registrada como corrupção enquanto a NVU está fora do GTO.
- Saída do GTO continua pausando leitura sem alterar o estado canônico; retorno retoma o mesmo estado.
- Telas desconhecidas continuam neutras e não promovem etapas.
- Frames da seleção em `WAITING_FREIGHT` continuam ordenados para não perder o instante do Aceitar.
- Frete confirmado continua imutável e aparece como `Frete atual em andamento`.
- Resultado real + Receber continuam sendo os únicos gatilhos de conclusão normal; persistência,
  selagem, fila durável e Firebase mantêm a ordem fail-closed.
- `Painel Sênior` fica visível para todo usuário já admitido no `AdminLayout` (que é protegido por
  `allowedRole="admin"`). O conteúdo Sênior continua protegido pelo gate próprio de senha/claim Firebase.

## Validação

- `npm run verify:release`: aprovado, sem falhas.
- 558 linhas de checks/assertions `PASS`/`OK`/`✓` no log completo da certificação executada, mais 10/10 checks específicos de navegação Sênior executados separadamente. O pipeline R3.28 final também inclui essa validação Sênior.
- R3.28 practical-flow: 17/17.
- R3.27 runtime-flow: 31/31.
- R3.26 deterministic-flow: 21/21.
- R3.25 detection-flow: 16/16.
- Native flow: 49/49.
- Automatic sync: 74/74.
- Senior navigation: 10/10.
- R3.17 selection integrity: 20/20.
- R3.16 release audit: 22/22.
- Java syntax check: PASS (15 fontes).
- Fixture exata da tela de fretes do teste: 1536x691, 5 linhas detectadas; seleção visual exercitada
  individualmente nas linhas 1, 2, 3, 4 e 5.

A homologação final de MediaProjection/UsageStats depende do Android/OEM real e deve ser executada
no aparelho. O pacote impede publicação caso a suíte ou a compilação Java real falhe no Windows.
