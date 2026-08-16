# GTO · FIX15 — Auditoria de causa raiz e correção da seleção rápida

## Sintoma confirmado no FIX14

O usuário conseguia abrir a lista e aceitar fretes, mas várias seleções não eram reconhecidas. O problema não era apenas limiar de OCR.

## Causas raiz encontradas

1. **O caminho rápido de seleção desviava do caminho que alimentava o snapshot/OCR.**
   Durante `WAITING_FREIGHT`, `onImageAvailable()` enviava os frames diretamente ao detector rápido e retornava. Assim, a lógica antiga que mantinha `latestFreightPanelFrame`, `freightOptions` e histórico OCR não era executada no momento crítico.

2. **A seleção dependia de existir um frame visual claramente pressionado.**
   Em um toque rápido, o GTO pode fechar a lista antes de a MediaProjection entregar um frame com destaque suficientemente forte no botão `Aceitar`. Se esse frame não existisse na fila analisada, nenhuma linha era armada.

3. **A versão anterior não tinha mais um timestamp independente do toque.**
   Sem Accessibility e sem `FLAG_WATCH_OUTSIDE_TOUCH` na bolinha, o detector não sabia quando aumentar a sensibilidade. Ele precisava inferir tudo apenas por diferenças entre frames, o que favorecia falsos negativos.

4. **Um candidato verdadeiro podia ser apagado por mudança de assinatura da página.**
   Frames de transição/pressionamento podem alterar a assinatura visual do painel. Limpar o candidato em `pageChanged` podia descartar a seleção antes da confirmação pelo desaparecimento da lista.

5. **O OCR exato podia não ter um frame limpo anterior ao clique.**
   O detector sabia a linha, mas o snapshot usado para ler os textos podia não estar preenchido pelo caminho rápido. Isso deixava o OCR dependente de um frame de transição.

6. **A separação `Empresa > empresa destino` era rígida demais.**
   O separador `>` é pequeno na interface do GTO. Quando o ML Kit não o reconhecia, o campo Empresa de origem podia ficar vazio e invalidar todo o frete apesar de a linha já estar correta.

## Arquitetura aplicada no FIX15

### 1. Pulso de toque independente da bolinha

Foi criado um overlay transparente de **1 × 1 px**, separado do botão NVU. Ele usa `FLAG_NOT_TOUCH_MODAL + FLAG_WATCH_OUTSIDE_TOUCH` somente durante `WAITING_FREIGHT`.

Esse sensor não tenta obter coordenadas (que no Motorola já chegaram como `0,0`). Ele registra apenas o instante do `ACTION_OUTSIDE`. A bolinha NVU continua sem `FLAG_WATCH_OUTSIDE_TOUCH`, preservando a estabilidade da abertura/fechamento.

### 2. Correlação toque + sequência de frames

O instante do toque arma uma janela curta. O motor compara os frames imediatamente anteriores e posteriores e reduz os thresholds somente nessa janela.

A seleção só é finalizada quando há duas evidências independentes:

- uma única linha apresenta alteração compatível com o botão `Aceitar` pressionado;
- em seguida, a lista de fretes desaparece.

Se o toque for em uma seta de página, existe pulso, mas a lista continua aberta; portanto o candidato é descartado.

### 3. Buffer para toque ultrarrápido

Os frames da lista são mantidos em um ring buffer curto. Se a comparação em tempo real não encontrar a linha antes de a lista fechar, o sistema revisa retrospectivamente todos os frames entre o toque e o desaparecimento da lista.

### 4. Snapshot pré-clique

O painel de fretes é salvo enquanto a lista está estável. Ao receber o pulso, o snapshot da mesma página é congelado. Depois de saber a linha selecionada, o OCR lê esse frame limpo, nunca a animação de fechamento.

### 5. Quantidade de fretes sem OCR

A quantidade é determinada pela estrutura visual dos botões laranja. Foram adicionadas validações de altura e espaçamento vertical para evitar que elementos laranja do gameplay sejam confundidos com lista de fretes.

Validação offline com screenshots reais fornecidos no desenvolvimento:

- página com 3 fretes: reconhecida como 3;
- páginas com 4 fretes: reconhecidas como 4;
- páginas com 5 fretes: reconhecidas como 5;
- gameplay normal que o detector antigo confundia com 2 botões: rejeitado como lista.

### 6. OCR somente da linha selecionada

Depois de confirmar a linha, o recorte é ampliado e o OCR trabalha apenas no cartão escolhido. A leitura procura:

- Carga;
- Empresa de origem;
- Destino;
- Distância;
- Ganhos.

O campo Empresa possui fallback geométrico baseado nos elementos/bounding boxes do ML Kit quando o caractere `>` não é reconhecido.

## Segurança contra frete errado

O motor não usa `ACTION_OUTSIDE` para escolher uma coordenada e não usa AccessibilityService. O pulso só muda o contexto temporal do detector. Uma seleção precisa ser confirmada pela mudança localizada da linha e pelo fechamento subsequente da lista.

Se não houver evidência suficiente, o estado retorna para seleção em vez de registrar outra linha por aproximação.

## Validações executadas antes do empacotamento

- `npm run lint`: aprovado;
- `npm run verify:project`: aprovado;
- `npm run validate:gto-native`: 32/32 verificações aprovadas;
- `GtoFastVisualDetector.java`: compilação isolada com stubs Android aprovada;
- detector visual executado offline contra screenshots reais: 3/4/5 linhas corretas e falso positivo conhecido rejeitado;
- AccessibilityService: ausente;
- captura de áudio: ausente;
- bolinha interativa: não usa `FLAG_WATCH_OUTSIDE_TOUCH`;
- sensor de pulso: isolado em 1 px e ativo somente no estado de seleção.

## Limite de validação

O build Gradle completo precisa ser executado no Android Studio/Windows porque o ambiente usado para a auditoria não consegue baixar a distribuição Gradle. A validação do comportamento físico final (latência específica do Motorola + frame exato produzido pelo GTO) depende do teste no aparelho, mas o FIX15 corrige as causas de código identificadas sem reintroduzir AccessibilityService.
