# NVU GTO R3.4 — Compatibilidade de tela, estabilidade do overlay e integridade de frete

Versão Android: **1.0.24** (`versionCode 24`)
Base: **R3.3 v1.0.23** + correção mais recente de navegação Sênior do Dev Google AI Studio.

## Diagnóstico da causa raiz

### 1. Detecção de frete dependente demais da geometria de referência
A R3.3 procurava a coluna laranja `Aceitar` prioritariamente em uma faixa fixa próxima de 91%-99,4% da largura capturada e o OCR preciso também usava recortes horizontais fixos. Isso funcionava nos aparelhos de referência, mas podia falhar quando o GTO reposicionava/reescalava o painel por resolução, proporção de tela, densidade ou comportamento de captura.

### 2. Cache textual da página anterior podia ser reutilizado na página nova
Quando a assinatura visual detectava mudança de página, uma nova geração de OCR era criada, mas `freightOptions` da página anterior permanecia disponível até o novo OCR terminar. Um toque rápido nesse intervalo podia associar a linha correta visualmente aos km/valor/origem/destino da página anterior.

### 3. Fallback sem marcador de toque podia escolher linha errada
O sensor transparente de 1 px (`FLAG_WATCH_OUTSIDE_TOUCH`) é uma evidência importante do instante do toque. Se um OEM rejeitasse esse overlay, a exceção era silenciosa e a seleção passava a depender mais do sinal visual passivo. O limiar passivo era permissivo demais para alguns frames animados.

### 4. Bolinha podia desaparecer por falso foreground ou overlay desconectado
`UsageEvents` podia entregar eventos transitórios de System UI/permission surfaces enquanto o GTO continuava por baixo. Esses eventos podiam iniciar o debounce de saída e esconder o overlay. Também não havia autorrecuperação quando o `View` existia em memória, mas já não estava anexado ao `WindowManager`.

### 5. Sincronização pendente podia ficar sem causa visível
A fila é corretamente vinculada ao Firebase UID. Porém, quando o UID nativo atual não correspondia ao `driverId` selado na entrega, a fila era preservada silenciosamente. Para o motorista, o menu continuava parecendo apenas “sincronizando”. Ausência de autenticação nativa também não tinha diagnóstico específico exposto.

## Correções aplicadas

- Detecção da coluna `Aceitar` em múltiplas faixas horizontais, com seleção da pilha mais plausível.
- Refinamento do limite horizontal real do botão depois da detecção, para que OCR e assinatura de página usem a posição real e não a borda da faixa de busca.
- OCR da lista com ROI adaptativa; fallback sem geometria usa uma região mais ampla e segura.
- Recorte da linha selecionada derivado do botão detectado.
- Número da página e fallback de coordenada do toque seguem a coluna detectada.
- Ao detectar página nova, o cache textual anterior é invalidado imediatamente.
- `stableFreightForRow()` só aceita dados cuja geração OCR corresponda à página visual atual.
- Conflito entre duas leituras de km/valor da mesma linha agora falha fechado: pede nova seleção em vez de registrar dado divergente.
- Fallback passivo exige evidência visual mais forte quando o sensor de toque não está ativo.
- Falha do sensor de toque deixa diagnóstico persistente; não é mais silenciosa.
- Eventos transitórios de System UI/permission controller não substituem o GTO como app real em primeiro plano.
- Eventos de foreground/background compatíveis com Android antigo e moderno são acompanhados separadamente.
- Overlay desconectado do `WindowManager` é detectado e recriado automaticamente.
- Dimensão da captura, densidade, API Android, banda detectada e conflitos de frete ficam disponíveis no status nativo para diagnóstico.
- UID divergente na fila de sincronização deixa de falhar silenciosamente e informa que a entrega está preservada.
- Ausência de autenticação Firebase nativa recebe código de diagnóstico explícito.
- Menu diferencia `SYNCING` de `PENDING`: quando o envio falha, informa que o registro está preservado e mostra a causa disponível.
- Correção de navegação Sênior do Dev atual foi incorporada ao Capacitor para manter web/APK alinhados.
- `MainActivity.onStart()` permanece `public`, preservando a correção de compilação.

## O que foi preservado

- Máquina de estados de viagem e cancelamento R2/R3.
- `GtoSelectionCoordinator` original.
- Captura pré-toque e janela crítica de frames.
- Finalização automática pela tela `Concluído`.
- Proteção contra ADS/bônus.
- Payload FIX18 selado com SHA-256.
- Fila offline, retry e idempotência.
- Backend `registerGtoTrip` não foi alterado nesta revisão.

## Validação

- `validate-gto-native-flow`: **47/47**
- `validate-gto-auto-sync`: **74/74**
- `audit-gto-fix18`: **26/26**
- `audit-gto-r2-lifecycle`: **25/25**
- `audit-gto-r3-guided-auto-flow`: **25/25**
- `audit-gto-r3-2-result-fallback`: **11/11**
- `audit-gto-r3-3-freight-rearm`: **21/21**
- `audit-gto-r3-4-device-compat`: **42/42**
- `validate-senior-navigation`: **10/10**
- Total das verificações estáticas/regressivas acima: **281/281**
- TypeScript/TSX: **139/139** arquivos analisados sem erro sintático.
- Detector Java adaptativo executado em teste sintético real: **432/432** combinações aprovadas (6 larguras × 6 proporções de tela × 6 posições horizontais × 2 níveis de luminância do botão).
- `GtoFastVisualDetector.java` compilou e executou no harness Java com stubs Android; isso também encontrou e permitiu corrigir uma chamada de método incompatível antes do empacotamento.

## Limite da validação no ambiente

Não foi possível executar um build Gradle Android completo neste ambiente porque a distribuição Gradle/SDK necessária não está disponível localmente e o ambiente de execução não possui acesso de rede para recuperá-la. O build final deve ser executado no Android Studio do ambiente oficial após `npm install` e `npm run cap:sync:android`.

## Teste de campo recomendado antes da distribuição ampla

Testar o mesmo APK assinado em pelo menos dois aparelhos com proporções/resoluções diferentes e, se disponível, um Android 7–9 e um Android recente. Confirmar: bolinha, leitura de 2 páginas diferentes de fretes, toque rápido, cancelamento/troca de frete, conclusão automática e sincronização.
