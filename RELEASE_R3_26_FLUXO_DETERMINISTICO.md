# NVU R3.26 — Fluxo GTO determinístico e seguro

## Objetivo

A R3.26 fecha a inconsistência observada no teste real em que a lista era detectada, o motorista tocava em **Aceitar**, porém o estado podia ser reinterpretado como uma nova lista e a confirmação do frete era perdida.

A automação agora separa explicitamente cinco dimensões: **estado da viagem**, **tela visual reconhecida**, **GTO em primeiro plano**, **estado da captura** e **estado de sincronização**. Um sinal isolado não pode sobrescrever os demais.

## Fluxo funcional

1. `WAITING_FREIGHT`
   - somente uma lista GTO visualmente válida pode iniciar a leitura dos fretes;
   - o motorista recebe `Lista de fretes detectada · X opções.`;
   - a geometria atual aceita de 1 a 6 fretes visíveis por página.

2. `CONFIRMING_FREIGHT`
   - a linha selecionada é congelada antes da confirmação;
   - a lista ainda visível após o toque não pode reiniciar a seleção;
   - uma transição visual N → N-1 causada pelo botão `Aceitar` pressionado preserva a quantidade real da lista;
   - OCR e dados independentes precisam concordar literalmente; não há correção/invenção de nomes;
   - existe watchdog de confirmação, evitando estado preso silenciosamente.

3. `TRIP_IN_PROGRESS`
   - o frete confirmado torna-se imutável;
   - o botão flutuante mostra **Frete atual em andamento** e os dados congelados;
   - reabrir a lista é informativo: a viagem atual permanece preservada;
   - uma troca de frete exige ação explícita do motorista e uma nova seleção real.

4. Saída do GTO
   - a leitura/processamento de tela é pausada na saída do primeiro plano;
   - o estado da viagem não é alterado nem limpo;
   - callbacks OCR iniciados antes da saída não podem promover estados enquanto outro app está na frente;
   - ao retornar ao GTO, a leitura é retomada no mesmo estado.

5. Telas desconhecidas
   - menus, notificações, Recents, configurações, outros aplicativos, animações e telas GTO ainda não implementadas são neutros;
   - uma tela desconhecida não é convertida por tempo/heurística em lista, resultado ou nova etapa.

6. Resultado e sincronização
   - somente o fluxo verdadeiro de resultado pode iniciar a conclusão;
   - `Receber` precisa de evidência válida;
   - o payload é selado localmente antes da rede;
   - falha de rede mantém fila durável para retry;
   - callbacks de sincronização canônica de estados/sessões antigas são ignorados.

## Correções de causa raiz

- `CONFIRMING_FREIGHT` foi removido do mecanismo que interpreta lista reaberta durante viagem.
- A oscilação visual `2 → 1`, `5 → 4`, etc. causada pelo botão pressionado não sobrescreve o snapshot limpo nem a contagem real.
- A leitura é pausada ao sair do GTO e retomada sem reset de viagem.
- Um aplicativo de terceiro conhecido em primeiro plano não pode ser confundido com GTO por pixels semelhantes.
- Telas desconhecidas não provocam transições.
- Mensagens ao motorista só são marcadas como entregues depois que o overlay foi anexado com sucesso.
- Erros de frame registram classe **e mensagem concreta**, em vez de apenas `RuntimeException`.
- Sincronização canônica ignora callbacks atrasados pertencentes a estado ou sessão anterior.

## Mensagens principais

- `Lista de fretes detectada · X opções.`
- `Frete identificado. Tudo preparado, podemos partir!`
- `Frete atual em andamento`
- `Leitura pausada · estado da viagem preservado`
- erros válidos exibem causa específica e mantêm diagnóstico interno.

## Compatibilidade/publicação

- Release: **R3.26**
- Web/source: **2.3.7**
- Android: **1.0.43**
- versionCode: **43**

A R3.26 altera tanto a camada Android quanto a camada Web de status. Portanto, depois de executar `PREPARAR-ANDROID-WINDOWS.bat`, publique **Firebase Functions**, publique o novo **Netlify `dist`** e só então gere/instale o APK.
