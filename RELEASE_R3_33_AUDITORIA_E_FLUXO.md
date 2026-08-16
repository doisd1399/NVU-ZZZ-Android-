# NVU R3.33 — Auditoria estrutural e correcoes cirurgicas do fluxo GTO

## Escopo

A R3.33 foi criada sobre a R3.32 depois de auditar a maquina de estados, captura MediaProjection, classificadores visuais, OCR, selecao, persistencia, fila local, ACK e interface do overlay.

## Causas raiz corrigidas

1. **Resultado real podia ficar inacessivel por estado stale**: um frete ja congelado no snapshot imutavel podia coexistir com runtime em `WAITING_FREIGHT`. Nesse caso a tela `Concluido/Receber` era roteada ao detector de lista em vez do detector de resultado.
   - Correcao: recuperacao estritamente condicionada ao snapshot imutavel da MESMA sessao (`freightLocked=true`), restaurando `TRIP_IN_PROGRESS` sem inferir viagem a partir de uma tela isolada.

2. **Overlay da propria NVU podia cobrir o texto da tela Concluido** na MediaProjection.
   - Correcao: quando o gate visual identifica um candidato real de resultado, o menu expandido e recolhido e o OCR e executado no frame seguinte, limpo.

3. **Gate visual de resultado permissivo demais** podia considerar regioes escuras do overlay/cenario como modal de resultado.
   - Correcao: assinatura reforcada do modal central e da metade direita, preservando o botao Receber e o bloco ADS sem aceitar gameplay/overlay normal.

4. **Estado Web/driver e sincronizacao**:
   - ACK de sessao anterior nao pode alterar a viagem atual.
   - Detalhe tecnico de erro fica no diagnostico; mensagem ao motorista permanece curta e verdadeira.
   - Sucesso so aparece depois do ACK real.

## Interface do overlay

- `Painel operacional` foi substituido por `Operacao`.
- `Operacao` mostra dados reais disponiveis: operacao, viagens, veiculo e reboque.
- Durante viagem permanece `Frete atual em andamento` sem a linha redundante `Viagem em andamento`.
- `Trocar frete atual` foi removido da experiencia normal.
- `Confirmar conclusao da entrega` foi removido da experiencia normal.
- Botoes foram compactados mantendo area de toque.
- Resultado confirmado mostra `Toque em Receber para concluir a viagem.`
- Apos Receber: `Enviando viagem automaticamente...`
- Somente depois do ACK: `Viagem enviada com sucesso!`
- Sem conectividade: `Sem conexao. Viagem salva e aguardando envio.`

## Minimizar, sair e retornar

- Sair temporariamente do GTO pausa somente a analise visual.
- Frete, resultado e fila duravel permanecem preservados.
- Ao retornar, evidencias visuais transitorias antigas sao descartadas, captura/toque sao rearmados e o mesmo estado e retomado.
- Nova permissao de MediaProjection so e solicitada quando os recursos de captura realmente nao existem mais.

## Fechamento real do GTO

A auditoria concluiu que uma aplicacao Android comum nao deve limpar automaticamente uma viagem apenas por `PAUSED/STOPPED`: esses eventos tambem ocorrem quando o simulador foi apenas minimizado/coberto. A API de historico de morte de processo para outro UID exige permissao privilegiada. Portanto a R3.33 **nao introduz um reset automatico inseguro baseado em foreground**. Uma viagem nao concluida continua preservada em ausencia de evidencia confiavel; cancelamento explicito permanece a forma segura de abandono.

## Fluxo esperado

`WAITING_FREIGHT -> FREIGHT_LIST_DETECTED -> CONFIRMING_FREIGHT -> TRIP_IN_PROGRESS -> RESULT_DETECTED -> RECEIVE_CONFIRMED -> SYNC_PENDING -> COMPLETED/NEXT_TRIP`

Telas desconhecidas continuam neutras e nao podem iniciar OCR/transicao fora do estado permitido.
