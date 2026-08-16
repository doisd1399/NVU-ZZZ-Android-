# NVU R3.32 — Lista por card real e continuidade

## Objetivo
Fechar falsos positivos de lista e manter o fluxo GTO contínuo ao alternar apps, perder/retomar captura e sincronizar viagens.

## Mudanças
- Uma lista real exige, no mesmo alinhamento: botão Aceitar + corpo escuro do card + texto claro do frete + informação verde de km/valor.
- Telas sem essa composição permanecem neutras e não iniciam OCR nem alteram o estado.
- Ao sair do GTO, o estado canônico é preservado. Ao retornar, coordenadas/frames transitórios são reconstruídos e o sensor de toque é rearmado.
- MediaProjection não é solicitada novamente apenas por alternar apps. Reautorização é exibida somente quando os recursos reais de captura deixaram de existir.
- Depois de Receber, um payload já selado pode liberar a próxima seleção enquanto sincroniza em segundo plano somente quando os metadados locais provam que não é a última entrega da operação.
- ACK de uma entrega anterior continua atualizando o progresso da operação sem sobrescrever a nova sessão ativa.

## Versão
- Web: 2.3.9 (inalterado)
- Android: 1.0.49
- versionCode: 49
