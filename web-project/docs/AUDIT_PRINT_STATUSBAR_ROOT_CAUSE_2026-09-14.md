# Auditoria crítica — Print e barra de status

## Evidências confirmadas

1. `RecordTrip.tsx` valida o comprovante e depois chama `completeTripWithCoordinator`; o erro genérico é emitido no `catch` quando o primeiro passo durável `addTrip` falha.
2. `TripsRepository.addTripWithinOperationLimit` chama `syncJobProgress` e, em seguida, executa uma transação que lê `trabalhos/{jobId}`, cria `historico_viagens/{tripId}` e atualiza `progress/status` do job.
3. `firestore.rules` permite ao motorista criar `historico_viagens` (`requestedTripDriverId() == request.auth.uid`), mas não permite ao motorista atualizar `progress` ou `status` arbitrários em `trabalhos`. Para motorista, as únicas atualizações de job autorizadas são: transição `pending -> active` apenas em `status`, ou conclusão com conjunto restrito de campos e `status == completed`.
4. Portanto, a transação atual é rejeitada por autorização no `transaction.update(jobRef, { progress, status })`; como o Firestore é atômico, a criação da viagem também é abortada. Esse é o ponto causal comprovado do erro de salvamento no Print, não OCR, upload ou `persistTripId`.
5. O caminho Pro chama o mesmo método de limite, portanto a correção precisa manter paridade Print/Pro.
6. Para conservar o limite sem ampliar regras, a correção deverá reservar cada slot com um ID determinístico e único dentro de uma transação que apenas leia o job e crie a viagem. A criação deve ser `transaction.create` ou leitura prévia do `tripRef` + `transaction.set`; nunca atualizar `trabalhos` no cliente. A leitura do job serializa concorrência e a criação fail-closed impede sobrescrita de slot.
7. `NvuStatusBarController` usa `useTheme()`/`frotalog-theme` e não consulta o tema Android. Contudo, `DriverLayout` também chama `SystemBars.setStyle` em `useLayoutEffect` em todas as rotas driver, incluindo o Print, criando dois escritores concorrentes. O status-bar fix deverá retirar o escritor concorrente somente do `/driver/trip`, mantendo as regras de outras páginas.
8. `MainActivity` aplica ícones escuros apenas como default de primeira pintura e não reaplica o tema Android no ciclo de vida. `useTheme` não usa `prefers-color-scheme`, logo a autoridade correta já é o tema NVU.

## Limites da validação

As regras não serão relaxadas nem alteradas nesta correção. A validação será por testes estáticos/regressões, TypeScript/lint, build Release e auditoria do APK. Validação física Motorola permanece pendente.


## Complemento — barra de status no tema claro do NVU (2026-09-15)

A causa remanescente foi confirmada no caminho nativo do Capacitor. O plugin `SystemBars` inicializava com `style=DEFAULT` quando não havia configuração no `capacitor.config.ts`; no Android, `DEFAULT` é convertido por `getStyleForTheme()` usando `Configuration.UI_MODE_NIGHT_MASK`, isto é, o tema nativo do dispositivo. Ao mesmo tempo, os estilos `AppTheme.NoActionBar` e `AppTheme.NoActionBarLaunch` declaravam `android:windowLightStatusBar=false`. Assim, antes de o React montar `NvuStatusBarController`, o Android podia aplicar conteúdo claro/branco, mesmo quando `frotalog-theme` era `light`.

A correção determinística foi aplicada em três camadas: `SystemBars: { style: "LIGHT" }` no `capacitor.config.ts`; `android:windowLightStatusBar=true` nos temas de lançamento e pós-splash; e normalização da barra final de pathname no controlador central, mantendo o mapeamento exclusivo `NVU light -> SystemBarsStyle.Light` e `NVU dark -> SystemBarsStyle.Dark`. O controlador de Driver continua excluído da rota Print para não sobrescrever a decisão central.

A validação estática confirmou que não há consulta a `prefers-color-scheme` nem a API de tema Android na lógica Web, que as rotas `/select-profile` e `/driver/trip` permanecem cobertas, que o plugin nativo inicia em LIGHT e que os temas Android não forçam mais `windowLightStatusBar=false`. A validação física em Motorola continua pendente até instalação do APK no dispositivo.
