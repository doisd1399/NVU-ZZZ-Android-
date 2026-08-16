# NVU R3.27 — Auditoria cirúrgica do fluxo GTO

## Escopo

Fluxo auditado: abertura do GTO → detecção real da lista → seleção exata da linha → congelamento do frete → viagem em andamento → tela real de resultado → toque em Receber → persistência local → fila selada → Firebase/ACK.

Versões: R3.27 / Web 2.3.8 / Android 1.0.44 / versionCode 44.

## Causas raiz encontradas e corrigidas

1. **Deadlock IDLE × foreground do GTO.** O Web podia abrir o GTO com o observador ainda em `IDLE`. Em aparelhos cujo UsageStats demora a reportar o GTO, os pixels da lista só podiam destravar a captura quando o estado já era `WAITING_FREIGHT`, criando dependência circular. Agora a abertura do GTO prepara `WAITING_FREIGHT` antes do primeiro frame e falha fechada se isso não for possível.

2. **Bridge visual não era renovada ao reabrir o GTO com MediaProjection já ativa.** Mesmo com `WAITING_FREIGHT`, o ImageReader podia descartar todos os frames enquanto o OEM ainda reportava NVU como foreground. Agora cada abertura explícita NVU→GTO cria uma janela curta e limitada em que somente a geometria estrita de uma lista real pode confirmar o GTO. Um aplicativo terceiro conhecido nunca pode ser sobrescrito por pixels parecidos.

3. **Frame do toque podia ser descartado.** `acquireLatestImage()` pode pular exatamente o sub-frame em que um `Aceitar` muda. Em `WAITING_FREIGHT` a fila passa a ser consumida em ordem com o detector visual leve, preservando o gesto temporal.

4. **Aceitar do meio podia transformar N botões em zero.** Quando um botão intermediário escurecia, o espaçamento dos N−1 restantes deixava de formar a pilha estrita e a evidência era descartada. O detector agora preserva esse N→N−1 somente como transição de pressão e identifica a linha ausente, sem tratá-lo como uma nova lista.

5. **Corrida entre OCR da página e OCR da linha selecionada.** Uma seleção muito rápida após abrir/trocar a página podia chegar antes da leitura independente da página. A R3.27 força a leitura da página atual usando o snapshot imutável pré-toque antes do OCR da linha. O throttle normal pode ser ignorado apenas nessa confirmação crítica; o bloqueio `ocrBusy` continua serializando ML Kit em aparelhos fracos. Página/generation divergente falha fechada.

6. **Status Web podia mostrar etapa antiga.** O painel priorizava o estado canônico do Firestore, que pode chegar depois do Android. Durante execução ativa, o estado nativo do APK é a autoridade da etapa atual; Firestore continua sendo o espelho durável. Mensagem antiga não pode sobrescrever um estado nativo mais novo.

7. **Receive fallback podia ser rebaixado por callback tardio.** Um `RECEIVE_FALLBACK_CONFIRMED` já persistido agora é tratado como latch de Receive e não pode ser apagado por OCR tardio da tela de resultado.

## Regras preservadas

- Sair do GTO pausa leitura sem alterar a etapa da viagem; retornar retoma o mesmo estado.
- Tela desconhecida, Recents, notificação, menu, outro app e superfícies ainda não implementadas são neutros.
- Lista reaberta durante `TRIP_IN_PROGRESS` é informativa; troca de frete exige ação explícita.
- `CONFIRMING_FREIGHT` não aceita reset por nova leitura da mesma lista.
- Dados selecionados são congelados e validados por acordo independente; texto visível não é “corrigido” por aproximação.
- Apenas tela real de resultado pode armar Receive.
- Receive é persistido antes da confirmação final.
- A conclusão é persistida antes de `RESULT_CONFIRMED` e antes do envio.
- Payload é selado e colocado em fila durável antes do Firebase; erro de rede mantém retry; fila só é removida após ACK.

## Validação executada

`npm run verify:release` concluiu com sucesso. As 19 suítes que reportam contagem totalizaram **539/539 verificações**.

A validação prática disponível neste ambiente utiliza o **detector de produção** sobre a captura real do GTO reportada no projeto (1536×691):

- reconhece exatamente 5 fretes;
- reconhece variantes reais de 1 a 5 linhas;
- mantém 5 linhas em 1024×461, 1280×576, 1536×691 e 1920×864;
- simula o estado visual de pressão de cada um dos cinco `Aceitar` e identifica a linha correta 1/2/3/4/5;
- a lista inalterada não produz seleção falsa.

Também há regressão genérica para 1 a 6 linhas na geometria atual, máquina de estados ponta a ponta e matriz de tela de resultado.

## Limite de validação

Este ambiente não possui o aparelho físico do motorista nem uma sessão executável do Global Truck Online. Portanto, a suíte valida o código real, os pixels reais fornecidos e as transições internas, mas **não substitui a homologação física final no Motorola com o GTO rodando**. Para evitar novo diagnóstico cego, erros de frame, permissões, foreground, estado, seleção e sincronização permanecem expostos no status do observador.
