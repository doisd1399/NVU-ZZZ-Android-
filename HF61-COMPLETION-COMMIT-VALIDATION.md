# NVU R3.34-PC-HF61 — Completion Commit Safe

Base auditada: `NVU-CAPACITOR-ANDROID-R3.34-PC-HF60-TERMINAL-SAFE`.

A entrega foi versionada como HF61 (Android `1.0.113`, `versionCode 113`) para não sobrescrever a identidade da HF60 enviada e para preservar explicitamente HF58 Cost Safe, HF59 Sync Safe e HF60 Terminal Safe.

## Causa raiz encontrada

A HF60 já possuía prova certificada de `Concluído` e fila durável, porém o encerramento ainda carregava decisões históricas que podiam atrasar ou bloquear o commit definitivo:

1. Proteções pós-resultado ligadas a ADS ainda podiam manter uma conclusão certificada em espera ou alterar o caminho de encerramento.
2. O valor final certificado ainda podia depender de uma segunda leitura OCR, mesmo quando a própria tela `Concluído` já era semanticamente válida.
3. Eventos/touches de ADS posteriores à conclusão ainda tinham caminhos capazes de disputar o estado do resultado antes do fechamento completo.
4. Em `RESULT_CONFIRMED`, uma nova lista real podia depender do ACK do backend anterior em cenários nos quais a operação não conseguia pré-criar a próxima sessão apenas por metadados. Isso contrariava a regra de que sincronização anterior não pode bloquear um novo frete real.

## Correção aplicada

- `Concluído` certificado agora cria um **commit terminal por sessão**, persistido de forma síncrona.
- O commit é ligado ao `gtoTripSessionId`; a mesma sessão não volta a ser tratada como viagem ativa.
- O primeiro valor monetário compatível da tela certificada é congelado imediatamente. O consenso de duas leituras permanece para caminhos incertos/recovery.
- Pós-commit, OCR de anúncio, anúncio assistido e touches de ADS são neutros para aquela viagem.
- A observação de ações do resultado é desativada para a sessão terminal, impedindo reabertura/rejeição/duplicação após a conclusão.
- A fila já existente continua selando o payload localmente por `sessionId` antes da rede e reaproveita o mesmo registro em retry.
- A viagem terminal pode finalizar localmente mesmo se o app/GTO perder foreground após a prova certificada.
- Uma **nova lista de fretes real e semanticamente certificada** pode iniciar a próxima sessão a partir de `RESULT_CONFIRMED` mesmo sem ACK da viagem anterior, mas somente se a sessão anterior já estiver terminal e duravelmente presente na fila.
- Nenhuma próxima viagem é fabricada automaticamente: continua obrigatório haver nova lista real e posteriormente nova seleção.
- O snapshot/queue da viagem anterior não é descartado quando a nova lista assume o fluxo.

## Proteção contra duplicidade

A proteção é por identidade de sessão, não por delay:

- terminal commit associado à sessão atual;
- fila persistente indexada por `sessionId`;
- `IN_FLIGHT` também por `sessionId`;
- retry reutiliza o item selado;
- resultado terminal deixa de aceitar novas ações de ADS/Receber;
- `WAITING_FREIGHT` não interpreta tela de resultado como nova viagem;
- nova sessão só nasce quando existe nova lista certificada.

## Arquivos modificados em relação à HF60 enviada

- `.github/workflows/build-android-release.yml`
- `android/app/build.gradle`
- `android/app/src/main/java/com/nvu/operacional/GtoObserverService.java`
- `package.json`
- `scripts/test-gto-r3-34-hf49-auto-result.mjs`
- `scripts/test-gto-r3-34-hf60-terminal-safe.mjs`
- `scripts/test-gto-r3-34-hf61-completion-commit.mjs` (novo)
- `COMANDOS-R3.34-HF61-RELEASE-WINDOWS.txt` (substitui o arquivo de comandos HF60)

Nenhuma alteração foi feita em `functions/src/gtoTrips.ts` ou `functions/src/gtoState.ts`; os hashes esperados da HF58 foram preservados.

## Validações executadas

Passaram:

- Java syntax: **53 fontes / PASS**
- HF58 Cost Safe: **21/21**
- HF59 Sync Safe: **29/29**
- HF60 Terminal Safe: **29/29**
- HF61 Completion Commit: **29/29**
- HF49 Automatic Result: **17/17**
- HF42 Result Proof/Readiness: **29/29**
- HF45 Critical Flow: **18/18**
- HF46 Receive Exit: **10/10**
- HF47 Certified Result Lifecycle: PASS
- HF53 AI Studio Alignment: PASS
- HF55 Return Result Recovery: **20/20**
- HF57 Instant Messages: **20/20**
- Money integrity fixtures: **10/10**
- `verify:project`: PASS
- `verify:cap-remote`: PASS
- `validate:gto-native`: **49/49**
- `validate:gto-auto-sync`: **74/74**
- Firebase static cost audit: **0 críticos / 0 avisos**

## Limite da validação neste ambiente

A compilação Gradle real foi tentada, mas o wrapper precisou baixar Gradle 8.14.3 e este ambiente não conseguiu resolver `services.gradle.org` (`UnknownHostException`). O ZIP também não traz as dependências npm compiladas de Functions por padrão.

Por isso, **não é correto declarar que o APK binário foi compilado aqui**. A fonte, os gates estáticos, os testes de regressão e a sintaxe Java foram validados. O build final deve ser executado pelo preparador/Android Studio/GitHub Actions do projeto, que possui acesso às dependências e à keystore oficial.

## Identidade de release

- applicationId: `com.nvu.operacional`
- versionName: `1.0.113`
- versionCode: `113`
- artefato esperado no workflow: `NVU-R3.34-PC-HF61-release.apk`

