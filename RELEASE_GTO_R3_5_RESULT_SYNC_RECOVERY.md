# NVU GTO R3.5 — Resultado, recuperação e sincronização

Versão Android: `1.0.25` / `versionCode 25`.

## Causas raiz confirmadas

1. **Timeout rígido de 1,8 s após `Receber`.** O fluxo aceitava como conclusão normal apenas um retorno quase imediato ao HUD. Se o GTO exibisse uma tela preta/logo de carregamento por mais de 1,8 s, a sessão entrava em `AWAITING_BONUS_VALIDATION`. O retorno posterior ao gameplay deixava de ser aceito como recebimento normal, prendendo o motorista em “Validando o recebimento”.
2. **O toque em `Receber` não tinha evidência independente confiável.** O sensor transparente de 1 px era ativado apenas para a lista de fretes. Os métodos antigos de toque preciso/externo não possuíam chamada ativa no projeto. Assim, em vários aparelhos a NVU precisava inferir a ação somente pela troca de tela.
3. **Lista de fretes não recuperava estados de resultado pendentes.** A detecção automática da lista invalidava apenas `TRIP_IN_PROGRESS`. Uma sessão presa em `RESULT_DETECTED` ou `AWAITING_BONUS_VALIDATION` era preservada mesmo com a lista de novos fretes claramente visível.
4. **`SYNCING` podia ficar preso no mesmo processo.** A chamada `registerGtoTrip` não possuía watchdog da aplicação. Se a Task Firebase não entregasse success/failure por problema de rede/stack do aparelho, o `sessionId` permanecia em `IN_FLIGHT` e novas tentativas eram ignoradas até reiniciar o processo.
5. **Falhas de autenticação nativa/UID nem sempre refrescavam imediatamente a UI do serviço.** O erro era salvo, mas alguns caminhos não chamavam o listener ativo.

## Correções R3.5

- O sensor auxiliar de 1 px agora também observa a tela de resultado e persiste `resultActionTouchAt`.
- A ação continua válida por até 10 s durante a transição normal do GTO; evidência explícita de ADS/bônus continua tendo prioridade e bloqueia o registro normal.
- A antiga janela rápida de 1,8 s foi mantida somente como fallback para aparelhos que bloqueiam `ACTION_OUTSIDE`.
- `AWAITING_BONUS_VALIDATION` deixou de ser um estado irreversível: gameplay/lista podem resolver a sessão posteriormente.
- A lista de fretes agora é analisada também em `RESULT_DETECTED` e `AWAITING_BONUS_VALIDATION`.
- Se a lista voltar e existir evidência recente da ação de resultado sem ADS, a entrega anterior é confirmada antes de qualquer nova seleção.
- Se a lista voltar e a entrega anterior não puder ser confirmada com segurança, a sessão incompleta é descartada como `UNRESOLVED_RESULT_FREIGHT_LIST_RETURNED`, uma nova sessão é criada e a própria página já visível é reutilizada para detectar o próximo frete.
- `RESULT_CONFIRMED` continua fora de qualquer caminho de cancelamento; entrega concluída/preservada nunca é descartada para liberar um novo frete.
- `registerGtoTrip` ganhou watchdog local de 25 s. Ausência de resposta converte `SYNCING` em `PENDING`, preserva a fila e agenda retry idempotente.
- Falta de autenticação nativa e divergência de UID notificam imediatamente o listener do serviço e exibem a causa real ao motorista.
- Mensagens `SYNC_PENDING` agora carregam o detalhe real do erro/retry em vez de sempre dizer apenas “aguardando conexão”.
- Metadados de ação de resultado são limpos ao criar uma nova sessão.

## Componentes deliberadamente preservados

- `GtoFastVisualDetector.java`: SHA-256 inalterado em relação à R3.4.
- `GtoSelectionCoordinator.java`: SHA-256 inalterado em relação à R3.4.
- `functions/src/gtoTrips.ts`: SHA-256 inalterado; nenhum deploy Firebase é exigido por esta revisão.
- Contrato FIX18, fila SHA-256, idempotência, OCR do frete e detecção adaptativa R3.4 permanecem intactos.

## Fluxos esperados após a correção

- Frete aceito → rota → `Concluído` → `Receber` → tela preta/logo por alguns segundos → gameplay → `RESULT_CONFIRMED` → fila durável → Firebase.
- `Concluído` → ADS/bônus detectado → viagem não registrada como normal.
- Estado de resultado não confirmado → lista de fretes volta → sessão antiga é encerrada e nova sessão fica pronta automaticamente.
- Entrega já confirmada → falha/timeout Firebase → permanece em fila `PENDING`; nunca é descartada.
- Firebase sem callback → watchdog em 25 s → `PENDING` + retry; não fica `SYNCING` indefinidamente.

## Validação

Suites principais: 304/304 verificações aprovadas (47 nativo + 74 auto-sync + 26 FIX18 + 25 R2 + 25 R3 + 21 R3.3 + 42 R3.4 + 34 R3.5 + 10 navegação sênior).

O Gradle completo não pôde ser executado neste ambiente porque o wrapper tentou baixar `gradle-8.14.3-all.zip` e não houve resolução DNS para `services.gradle.org`. Uma verificação `javac` sem Android SDK não encontrou erros de parsing/sintaxe nos dois arquivos Java alterados; os únicos diagnósticos foram dependências Android/Firebase ausentes no classpath.
