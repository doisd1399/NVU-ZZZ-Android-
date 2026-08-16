# GTO — Etapa 1: Observador nativo

## Objetivo

Validar no Android real se o NVU consegue acompanhar o Global Truck Online (GTO) sem alterar o fluxo manual existente e sem gravar viagens automaticamente no Firebase nesta etapa.

## Escopo implementado

- Módulo Android/Capacitor específico para o pacote `com.stargamesapps.gto`.
- Botão flutuante NVU mostrado apenas quando o GTO é detectado em primeiro plano.
- Menu flutuante com:
  - **Iniciar viagem**;
  - **Painel operacional**;
  - **Cancelar viagem** quando uma observação está ativa.
- Captura de tela autorizada pelo próprio Android via MediaProjection.
- OCR local com ML Kit Text Recognition (modelo Latin empacotado no APK).
- Máquina de estados local:
  - `IDLE`
  - `WAITING_FREIGHT`
  - `TRIP_IN_PROGRESS`
  - `RESULT_DETECTED`
  - `AWAITING_BONUS_VALIDATION`
  - `RESULT_CONFIRMED`
  - `REJECTED_BONUS`
  - `CANCELLED`
- Detecção experimental da lista de fretes e associação do toque em **Aceitar** ao cartão visível naquele momento.
- Armazenamento local temporário dos dados reconhecidos do frete: carga, rota/empresas exibidas, destino visível, km e valor oferecido.
- Detecção da tela **Concluído / Valor a receber** e do valor final.
- Fluxo de validação do botão **Receber** e bloqueio seguro do caminho de **Dobrar valor (ADS)** enquanto o bônus é verificado.
- Bridge nativa para abrir o painel operacional do NVU.
- Cartão de configuração/diagnóstico na tela **Lançar Viagem** somente quando a empresa atual pertence ao GTO.

## O que NÃO foi alterado

- O lançamento manual de viagens continua funcionando.
- Nenhuma viagem observada pelo GTO é gravada automaticamente no Firestore nesta etapa.
- Ranking, histórico, progresso da operação, contratos e regras do Firebase não foram alterados.
- ATS, ETS 2 e outros simuladores não usam este observador.

## Primeiro teste no aparelho

1. Gere/instale um novo APK, pois esta etapa contém código Android nativo.
2. Entre no NVU com um motorista vinculado a uma empresa GTO e abra **Lançar Viagem**.
3. No cartão **Automação GTO · Etapa 1**:
   - autorize **sobreposição sobre outros apps**;
   - autorize **acesso de uso**;
   - ative o observador.
4. Abra o Global Truck Online pelo botão do cartão ou normalmente.
5. Confirme que o botão **NVU** aparece no GTO e desaparece ao sair dele.
6. Toque no botão NVU > **Iniciar viagem**.
7. Autorize a leitura/captura da tela quando o Android solicitar.
8. Navegue pelas páginas de fretes e escolha um frete em **Aceitar**.
9. Confirme que aparece **Frete detectado. Viagem em andamento.**
10. Conclua a entrega no jogo.
11. No resultado normal, use **Receber** e confirme a mensagem de validação.
12. Em um segundo teste, use **Dobrar valor (ADS)** e verifique se o estado não é aceito como resultado normal e se uma tela de bônus/vídeo é marcada como rejeitada.
13. Volte ao NVU e consulte no cartão de diagnóstico: estado, tela detectada, frete, valor e último evento.

## Pontos que este teste precisa confirmar

- Se todas as páginas de fretes do GTO mantêm o mesmo layout textual.
- Se o toque em **Aceitar** é observado de forma consistente no aparelho real.
- Quais textos/telas aparecem exatamente depois de assistir ao anúncio de valor dobrado.
- Como o GTO expõe a **origem** da rota. No print fornecido, o destino/rota é identificável, mas a origem ainda não está comprovada de forma segura.
- Variações de resolução, escala de interface e orientação horizontal.

## Próxima etapa após validação

Somente depois desses sinais serem confirmados no aparelho real:

1. consolidar origem/destino;
2. normalizar km e valor;
3. criar payload assinado/contextualizado com motorista, empresa, operação, contrato, veículo e reboque;
4. adicionar uma função de backend específica para submissão automática GTO;
5. atualizar progresso/histórico/ranking pelo mesmo modelo canônico já usado pela plataforma;
6. manter rejeição fail-closed para bônus/anúncio ou leitura ambígua.

## Arquivos principais adicionados/alterados

- `android/app/src/main/java/com/nvu/operacional/GtoObserverPlugin.java`
- `android/app/src/main/java/com/nvu/operacional/GtoObserverService.java`
- `android/app/src/main/java/com/nvu/operacional/GtoProjectionPermissionActivity.java`
- `android/app/src/main/java/com/nvu/operacional/MainActivity.java`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/build.gradle`
- `android/app/src/main/res/values/styles.xml`
- `src/lib/gtoObserver.ts`
- `src/components/GtoObserverSetup.tsx`
- `src/pages/driver/RecordTrip.tsx`
- `src/App.tsx`

## Validação executada neste pacote

- `npm run lint` / TypeScript (`tsc --noEmit`): aprovado.
- O APK Android não foi compilado neste ambiente porque o Android SDK do projeto aponta para a instalação local do Windows. A validação final do código nativo deve ser feita pelo Gradle/Android Studio no ambiente de build do projeto.
