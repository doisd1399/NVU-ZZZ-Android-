# GTO Etapa 1 — FIX7 Precisão e rapidez

Esta versão atua somente na camada nativa Android/Capacitor do observador GTO.

## Correções principais

- OCR da lista de fretes passa a analisar somente o painel direito do GTO em resolução nativa, evitando reduzir o texto pequeno da lista.
- As linhas dos fretes são ancoradas visualmente pelos botões laranja `Aceitar`, em vez de depender do OCR do texto do botão.
- O detector visual separa corretamente páginas com 3, 4 ou 5 fretes e evita unir dois cartões vizinhos.
- Cada cartão é isolado geometricamente antes de extrair carga, empresa de origem, destino, km e ganhos.
- Leituras consecutivas da mesma página são combinadas por consenso. Um único quadro ruim não substitui os dados estáveis.
- Km e ganhos possuem leitura numérica tolerante a confusões comuns de OCR como O/0 e I/1.
- A seleção usa a alteração visual do botão `Aceitar` e mantém a coordenada do toque apenas como auxílio quando o Android realmente a fornece.
- A saída da lista precisa ser confirmada em mais de um quadro, evitando confundir troca de página com aceite de frete.
- O sistema opera em modo seguro: se carga, empresa, destino, km ou ganhos não atingirem confiança suficiente, a viagem não é iniciada com dados inventados.
- A tela de resultado continua sendo observada e a leitura de `Valor a receber` foi priorizada em uma região central de alta resolução.
- Após autorizar a captura, o Android tenta devolver o foco diretamente ao GTO para evitar deixar o NVU aberto em uma janela flutuante do sistema.
- `MainActivity` e a Activity de autorização foram marcadas como não redimensionáveis para reduzir aberturas OEM em modo freeform.
- Mensagens do overlay foram simplificadas e deixadas mais amigáveis; diagnósticos técnicos continuam gravados internamente, mas não poluem o menu normal.

## Regra de segurança

Nenhum OCR de tela pode ser matematicamente garantido em 100% de todos os aparelhos/resoluções. Por isso esta versão prioriza **não registrar um frete errado**: quando a confirmação não é suficiente, ela pede nova seleção em vez de adivinhar.

## Validação feita neste pacote

- TypeScript: `npm run lint` passou sem erros.
- XML do Manifest: validado como XML bem formado.
- Sintaxe Java: verificada com `javac` até a etapa de resolução das bibliotecas Android; não foram encontrados erros sintáticos.
- O build Gradle completo não pôde ser executado neste ambiente porque o wrapper tentou baixar o Gradle da internet.
- O detector visual de botões foi conferido nos prints fornecidos e encontrou exatamente 3, 4 e 5 botões nas respectivas telas de frete.
