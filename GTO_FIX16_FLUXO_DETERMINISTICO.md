# NVU GTO — FIX16 · Correlação determinística de seleção

## Objetivo
Eliminar os bloqueadores encontrados na auditoria do FIX15 no caminho crítico:
`lista -> toque -> frame -> linha -> snapshot -> OCR -> viagem em andamento`.

## Causas raiz corrigidas

1. O toque não depende mais de `lastFreightListSeenAt`. Um toque muito rápido é enfileirado mesmo antes do primeiro reconhecimento completo da lista.
2. O marcador do toque é enviado ao mesmo `Handler` usado pelo `ImageReader`. A correlação usa sequência monotônica de frames, não horário de callback.
3. A captura é híbrida: `acquireLatestImage()` durante navegação normal e `acquireNextImage()` apenas na janela crítica após o toque.
4. O sensor 1x1 não limpa mais o motor de seleção quando é removido da UI.
5. A página/linha selecionada é copiada para `FreightSelectionTransaction` antes de mudar para `CONFIRMING_FREIGHT`. O OCR passa a possuir uma cópia independente do bitmap e da geometria.
6. O detector agora aceita o caso em que o botão pressionado fica escuro e temporariamente desaparece da máscara laranja. Esse caso estava bloqueado por uma validação de lista executada cedo demais.
7. Navegação entre páginas é separada de clique em Aceitar pela assinatura visual do painel. O limite de `samePage` foi reduzido com base nas capturas reais do GTO para impedir reaproveitamento da página anterior.
8. A lista é pré-lida por OCR em segundo plano assim que uma página é detectada. Isso cria um fallback da mesma linha para carga/empresa/destino/km/ganhos sem deixar OCR decidir qual linha foi clicada.
9. OCR de uma página antiga possui `generation id` e não pode sobrescrever a página atual depois de navegação rápida.
10. Não existe AccessibilityService, captura de áudio ou injeção de toque.

## Validações realizadas

- TypeScript: aprovado (`tsc --noEmit`).
- Pré-verificação estrutural: aprovada.
- Validação nativa GTO: 45/45.
- `GtoSelectionCoordinatorTest`: aprovado, incluindo toque antes do primeiro frame reconhecido.
- Detector visual Java: aprovado para clique em primeira, intermediária e última linha em cenário sintético.
- Detector visual Java sobre screenshots reais: reconheceu páginas com 3, 4 e 5 fretes e rejeitou telas de gameplay como lista.
- Teste de navegação: página 5->4 não é interpretada como clique em frete.
- Teste de botão escurecido/desaparecendo: linha correta identificada.
- Análise léxica Java: chaves balanceadas e nenhum diagnóstico de sintaxe do `javac`; o runtime não possui Android SDK para resolver os tipos Android e executar o Gradle completo.

## Limite de validação

Os testes acima validam o motor e os screenshots reais disponíveis. O comportamento final do `MediaProjection`, `WindowManager` e do Game Mode/OEM só pode ser confirmado executando o APK no aparelho Android real. Por isso este documento não declara uma garantia matemática de 100% em todo hardware; ele registra as causas objetivas corrigidas e os testes reproduzíveis executados antes do empacotamento.
