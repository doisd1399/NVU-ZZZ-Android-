# NVU GTO R3.8 — Certificação de confiabilidade do código

Versão Android: `1.0.28`  
Version code: `28`  
Base: R3.7 + hardening R3.8 de lifecycle, MediaProjection, overlay, resultado, logout e diagnóstico.

## Escopo auditado

- Inicialização e recuperação do `GtoObserverService`.
- Botão flutuante, menu e avisos de overlay.
- Mudança temporária de aplicativo e remoção da task da NVU.
- Morte/recriação do processo e restauração de viagem ativa.
- Permissões de overlay, Usage Access e MediaProjection.
- Encerramento inesperado da MediaProjection pelo Android.
- Resize da captura e callbacks atrasados entre gerações de projeção.
- Detecção da lista de fretes, troca de página, toque rápido e cache de OCR.
- Proteção contra associação de km/valor de outro frete.
- Cancelamento/abandono de rota e reaparecimento da lista de fretes.
- Detecção da tela `Concluído` em diferentes dimensões.
- Toque exato em `Receber`, ADS e toque sem coordenada utilizável.
- Falha do sensor transparente de toque em OEMs.
- Recuperação do valor final a partir de captura local temporária.
- Persistência da conclusão antes da chamada de rede.
- Fila durável, checksum, idempotência, watchdog e retry Firebase.
- Falta de autenticação nativa e UID diferente do motorista.
- Logout em aparelho compartilhado.
- Backup/restauração indevida de estado GTO.
- Alinhamento do frontend/backend com o Dev atual do Google AI Studio.
- Navegação do Painel Sênior preservada.

## Correções adicionais encontradas na auditoria R3.8

### 1. Falha terminal da autorização de captura

A Activity isolada de MediaProjection podia falhar antes de devolver um resultado ao serviço. Nessa condição, o serviço podia manter `projectionPermissionInFlight=true` em memória e tratar o System UI como transitório indefinidamente.

R3.8 adiciona callback terminal para `MANAGER_UNAVAILABLE`, `CONSENT_LAUNCH_FAILED` e `SERVICE_DISPATCH_FAILED`, limpando a trava e expondo o erro.

### 2. Callback de resize antigo contra uma projeção nova

Um runnable de resize já enfileirado podia executar depois de uma nova autorização de captura. Agora o resize é vinculado simultaneamente à geração, ao `VirtualDisplay` e ao `Handler` que o originaram. Um callback antigo não pode substituir `ImageReader` de uma sessão nova.

### 3. Falha do sensor de toque na tela de resultado

Em aparelhos/OEMs que recusam o overlay transparente de 1 px, a seleção de frete já possuía fallback visual, mas a etapa `Receber` podia ficar sem caminho seguro.

Agora existe fallback fail-closed: a NVU nunca adivinha `Receber`. Quando há continuidade comprovada do GTO e a tela de resultado desaparece, o motorista recebe a opção explícita `Confirmar recebimento`; também pode descartar a entrega não confirmada e iniciar um novo frete. Se a continuidade do GTO for quebrada, esse fallback é bloqueado.

### 4. Resultado reconhecido, mas valor final temporariamente ilegível

Se a tela `Concluído` fosse reconhecida, o motorista tocasse rapidamente em `Receber`, mas o OCR não conseguisse extrair o valor naquele quadro, a conclusão podia ficar esperando o valor para sempre.

R3.8 preserva temporariamente a região do resultado em `getNoBackupFilesDir()`, tenta recuperar o valor por OCR e só então sela a conclusão. O arquivo é local, não é enviado ao Firebase e é removido após uso/limpeza.

### 5. Recuperação do foreground service após erro de captura

Foram encontrados caminhos onde `startForegroundForTypes(false)` era chamado dentro do tratamento de erro sem proteção adicional. Se o próprio Android recusisse essa restauração, uma segunda exceção poderia derrubar o serviço. Todos esses caminhos agora são exception-safe e registram `startError`.

### 6. Erro de notificação do serviço

`NotificationManager.notify()` agora é protegido. Uma falha não derruba o observador e passa a gerar `notificationError` diagnosticável.

### 7. Logout e aparelho compartilhado

O logout agora:

- preserva na fila apenas uma entrega realmente concluída e ainda não sincronizada;
- descarta snapshot de sessão inacabada;
- remove captura local temporária do resultado;
- limpa latches `Receber`/fallback e permissão em andamento;
- usa `commit()` no boundary de conta;
- tenta encerrar o serviço e expõe falha se até o fallback de `stopService()` falhar.

### 8. Limpeza de captura local

Falhas ao remover a captura temporária do resultado não são mais ignoradas silenciosamente; ficam em `resultSnapshotError`.

## Comportamento esperado por cenário

| Cenário | Resultado esperado |
|---|---|
| Motorista inicia trabalho | Serviço confirma heartbeat antes de reportar sucesso |
| Entra no GTO | Bolinha aparece; se for desanexada pelo Android, tenta se reconstruir |
| Minimiza o GTO e volta | Viagem ativa permanece |
| NVU é removida dos recentes | Serviço não é intencionalmente parado; viagem/fila permanecem |
| Processo da NVU é recriado | Viagem ativa é restaurada; MediaProjection precisa ser autorizada novamente |
| Lista de fretes reaparece antes da conclusão | Sessão inacabada anterior é descartada e nova seleção é armada |
| Lista reaparece depois de `Receber` confirmado | Entrega concluída vence; não é descartada como rota antiga |
| Toque rápido em frete | Correlação usa sequência/pre-touch snapshot e rearm antecipado |
| OCR de página antiga termina atrasado | Geração antiga não pode sobrescrever página nova |
| Leituras de km/valor entram em conflito | Confirmação é bloqueada; não escolhe valor por adivinhação |
| Tela `Concluído` aparece | Gate leve acorda OCR; OCR continua autoridade semântica |
| Toca exatamente `Receber` | Conclusão é latch durável, sem timeout |
| Toca ADS | Caminho normal é rejeitado |
| Android não informa coordenada do toque | Ação fica pendente e é resolvida por transição segura; não expira |
| Sensor de toque não pode ser criado | Fallback explícito, fail-closed; nenhuma conclusão automática ambígua |
| Valor final não foi lido antes de fechar resultado | Recupera a partir da captura local temporária |
| Sem internet/Firebase indisponível | Payload continua selado na fila; retry com backoff |
| Callable não responde | Watchdog de 25 s volta estado para `PENDING`; não fica `SYNCING` infinito |
| Backend responde duas vezes/retry duplica chamada | `sessionId` + fingerprint mantêm idempotência |
| UID nativo não corresponde ao motorista | Envio é bloqueado e erro explícito; fila é preservada |
| Operação atingiu total de entregas | Nova viagem é bloqueada e motorista é orientado a iniciar nova operação |
| Logout | Sessão inacabada é eliminada; entrega concluída pendente permanece na fila correta |

## Validações executadas

Todos os validadores funcionais e regressivos incluídos no projeto foram executados após as correções finais.

Resultado consolidado: **738/738 verificações aprovadas** nos conjuntos executados. Esse total contém reexecuções intencionais de validadores inferiores por auditorias superiores.

Adicionalmente:

- `110/110` verificações da certificação específica R3.8.
- `34/34` cenários modelados de runtime/falha R3.8.
- `9/9` escalas da tela real `Concluído` fornecida pelo usuário passaram no pre-filtro visual.
- `javac` não apontou erro de sintaxe Java nos arquivos modificados; erros de símbolos Android/Firebase são esperados sem o classpath/SDK Android neste ambiente.
- `functions/src` permanece byte a byte alinhado ao último Dev Google AI Studio auditado.
- Entre `src`, apenas `lib/gtoObserver.ts` e `components/GtoObserverSetup.tsx` diferem intencionalmente para expor diagnósticos nativos da R3.8.
- `GtoFastVisualDetector.java`, `GtoSelectionCoordinator.java` e `functions/src/gtoTrips.ts` mantêm os hashes da base estabilizada, evitando regressão do algoritmo principal e do contrato backend.

## Limite de certificação

Esta auditoria certifica o código e os cenários reproduzíveis no ambiente disponível, mas não pode prometer comportamento absoluto contra ações que o próprio Android impede um aplicativo de controlar. Em especial, um `Forçar parada` feito pelo usuário/sistema impede a retomada automática até o aplicativo ser aberto novamente, e um token MediaProjection não sobrevive à morte completa do processo. Nesses casos a R3.8 preserva os dados duráveis e solicita nova autorização quando possível.

O build final Gradle/APK precisa ser feito no Android Studio do ambiente de produção. Este ambiente de auditoria não possui o SDK/Gradle completo e não conseguiu obter dependências externas por DNS, portanto não foi possível executar aqui o APK compilado em aparelhos físicos.

## Procedimento obrigatório antes de distribuição

1. Extrair a R3.8 em pasta nova.
2. `npm install`
3. `npm run cap:sync:android`
4. Confirmar todos os validadores sem `FAIL`.
5. `npm run cap:open:android`
6. Aguardar Gradle Sync.
7. Gerar APK release assinado com a mesma chave oficial.
8. Fazer smoke test físico em pelo menos um aparelho que apresentava falha e um aparelho de versão Android diferente antes da distribuição em massa.
