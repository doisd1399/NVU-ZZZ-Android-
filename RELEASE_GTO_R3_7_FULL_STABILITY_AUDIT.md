# NVU GTO R3.7 — Auditoria completa de estabilidade

Versão Android: `1.0.27`  
Version code: `27`

## Escopo

Auditoria completa do fluxo nativo GTO: inicialização do observador, permissões, MediaProjection, botão flutuante, detecção/seleção de fretes, persistência da viagem, tela de resultado, toque em Receber, fila local, sincronização Firebase, recuperação de erros e compatibilidade entre versões/tamanhos de tela.

## Causas raiz encontradas e corrigidas

1. **Estado GTO podia voltar após reinstalação.** O Android Auto Backup inclui SharedPreferences por padrão. As preferências de sessão, fila, retry, snapshot e quarentena do GTO não estavam excluídas do backup. Foram adicionadas regras `backup_rules.xml` e `data_extraction_rules.xml` para impedir restauração de estado transitório do GTO.

2. **Corrida de MediaProjection.** Um callback `onStop()` de uma sessão antiga podia chegar depois de uma nova autorização e liberar os recursos da captura nova. Foi introduzida uma geração de projeção para que callbacks antigos sejam ignorados.

3. **MediaProjection encerrada pelo Android era pouco visível.** O serviço agora diferencia captura parada/reautorização necessária, expõe diagnóstico ao bridge e orienta o motorista a autorizar novamente quando necessário.

4. **Sensor de toque de 1 px podia permanecer ativo sem captura.** Agora ele só existe enquanto a projeção está realmente ativa.

5. **Revogação de permissão podia deixar o observador em estado aparentemente ativo.** Overlay e Usage Access são verificados em runtime; erros são persistidos, mostrados e os overlays são recolhidos de forma segura.

6. **Eventos transitórios do System UI podiam fazer a bolinha desaparecer.** O cálculo de foreground agora preserva a posse do GTO diante de eventos transitórios de System UI/permission controller, respeitando o debounce existente.

7. **Falha ao mover a bolinha era silenciosa.** Exceções de `updateViewLayout` agora entram no mesmo mecanismo de diagnóstico e recuperação do overlay.

8. **Inconsistência de geometria entre detector e validador de lista.** O detector adaptativo aceitava escalas que o `hasFreightList()` rejeitava logo depois. Os limites foram alinhados, reduzindo diferença entre proporções, densidades e escalas de UI.

9. **Inicialização do serviço podia gerar falso erro em aparelhos lentos.** `startObserver()` e `recoverObserver()` agora aguardam heartbeat real por uma janela maior antes de declarar falha.

10. **Falhas ao abrir a tela de consentimento de captura podiam terminar silenciosamente.** Falta de MediaProjectionManager ou falha ao iniciar o consentimento agora geram códigos de diagnóstico persistentes.

11. **Persistência da conclusão tinha ordem insegura.** O estado `RESULT_CONFIRMED` só é assumido depois que ganho/status/sync foram gravados de forma síncrona. Se a gravação falhar, o latch de Receber continua preservado e o envio não começa com payload incompleto.

12. **OCR atrasado podia sobrescrever o estado após Receber.** Um callback tardio de OCR não consegue mais desfazer `RECEIVE_LATCHED`.

13. **Valor final ausente após Receber.** Antes de desistir, a NVU tenta recuperar o valor do OCR persistido. Se ainda não houver valor seguro, a viagem permanece bloqueada/preservada e não é enviada com valor incorreto.

14. **Falha rara ao arrastar a bolinha podia terminar em exceção no ACTION_UP.** Se `updateViewLayout()` falhasse durante o movimento, a R3.6/R3.7 inicial já removia o overlay quebrado, porém o evento `ACTION_UP` ainda podia tentar ler o `LayoutParams` já limpo. O fluxo agora faz guarda nula e agenda a recriação da bolinha.

15. **Diagnósticos nativos existiam, mas alguns não apareciam no painel.** Erros de projeção, painel flutuante, chip de status, sensor de toque, conflito de frete, sincronização e integridade agora são exibidos no diagnóstico do observador, evitando falhas silenciosas.

16. **Fila Firebase presa em SYNCING.** Mantido watchdog de 25 s: ausência de callback devolve o item a `PENDING`, agenda retry e preserva a viagem localmente.

## Compatibilidade Android

- `minSdkVersion 24`, `compileSdkVersion 36`, `targetSdkVersion 36`.
- Android 7.x (API 24–25): overlay legado `TYPE_PHONE` conforme compatibilidade da base.
- Android 8+ (API 26+): `TYPE_APPLICATION_OVERLAY`.
- Foreground detection suporta eventos antigos `MOVE_TO_FOREGROUND/BACKGROUND` e eventos modernos `ACTIVITY_RESUMED/PAUSED/STOPPED`.
- MediaProjection usa caminhos condicionais por API; recursos de Android 14+ não são exigidos em versões anteriores.

## Tela e geometria

O caminho de frete continua usando detector visual sem OCR no caminho rápido e geometria adaptativa da coluna de botões. O validador de lista foi alinhado com os mesmos limites de escala do detector.

Teste sintético executado com a implementação Java real do `GtoFastVisualDetector`:

- 6 larguras de tela;
- 6 proporções;
- 6 posições horizontais da coluna de botões;
- 3 alturas de botão;
- 3 espaçamentos verticais;
- 2 níveis de luminância.

Resultado: **3888/3888 combinações aprovadas** no envelope geométrico realista testado.

## Validações automatizadas

- GTO nativo: **47/47**
- Autosync Android/Firebase: **74/74**
- FIX18: **26/26**
- Ciclo de vida R2: **25/25**
- Finalização automática R3: **25/25**
- Rearm de fretes R3.3: **21/21**
- Compatibilidade R3.4: **42/42**
- Recuperação resultado/sync R3.5: **34/34**
- Receive latch R3.6: **32/32**
- Auditoria completa R3.7: **63/63**
- Navegação Sênior: **10/10**

Total dos conjuntos listados: **399/399 verificações**, além de **3888/3888** combinações sintéticas de geometria.

## Limites de certificação

A auditoria elimina os erros silenciosos e corrige as condições de corrida encontradas no código. Ainda assim, nenhum aplicativo Android pode garantir que um overlay nunca será retirado se o usuário revogar permissões, fizer Forçar parada, o sistema encerrar o processo, a MediaProjection for revogada ou um fabricante aplicar política agressiva de bateria. A R3.7 agora detecta/expõe esses estados e recupera automaticamente quando a plataforma permite.

O ambiente de auditoria não possui uma instalação Android SDK/Gradle completa com os mesmos componentes da máquina de release; portanto o APK release deve ser compilado no Android Studio e testado em aparelhos reais antes de distribuição ampla.

## Backend

`functions/src/gtoTrips.ts` não foi alterado nesta revisão. Não é necessário novo deploy da Cloud Function apenas para as correções R3.7.
