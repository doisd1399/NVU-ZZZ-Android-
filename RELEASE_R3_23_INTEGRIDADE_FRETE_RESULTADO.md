# NVU R3.23 — integridade do frete selecionado e do resultado

## Identificação

- Release funcional: R3.23
- Web: 2.3.4
- Android: 1.0.40 (`versionCode 40`)
- Application ID: `com.nvu.operacional`
- Android mínimo: API 24 (Android 7.0)

## Causa raiz confirmada

A R3.22 identificava a linha tocada, mas serializava o frete para o painel com os
campos `row` e `km`. O snapshot durável exigia `selectedRow` e `distanceKm`.
Assim, o toque podia estar correto e o bloqueio final ainda recusava o frete,
retornando o motorista à etapa “Escolha seu frete”.

Também existia um risco de integridade textual: o OCR dedicado da linha podia
substituir o destino estabilizado sem comparar carga, empresas e cidade. Uma
leitura como “Itapetona” poderia, em tese, substituir “Itapetuna” se quilômetros
e valor coincidissem.

## Correções

1. O JSON agora grava os nomes canônicos `selectedRow` e `distanceKm`, mantendo
   `row` e `km` como aliases compatíveis com o painel Web.
2. A linha exata e a leitura independente da lista precisam concordar em carga,
   empresa de origem, empresa de destino, destino, quilômetros e valor.
3. Comparações de nomes preservam acentos e letras; não há correção ortográfica,
   aproximação ou remoção de acentos para confirmar campos visíveis.
4. Cada campo exige pelo menos duas evidências concordantes. Uma leitura isolada
   não pode mais ser usada como fallback silencioso.
5. A relação empresa/cidade só é aprendida depois que o frete completo foi
   bloqueado no snapshot imutável.
6. A origem exata de contratos detalhados é enviada pelo runtime Web; operações
   sem origem verificável são bloqueadas com mensagem explícita, sem inventar cidade.
7. O valor final exige consenso entre duas passagens de OCR e, depois de lacrado,
   não pode ser substituído por uma leitura divergente.
8. A bolinha, o painel e os avisos são reposicionados para fora da lista de fretes.
   Se não houver espaço, o painel não abre sobre os cartões.
9. Quando uma notificação externa ou outro elemento deixa a linha ilegível, o app
   informa a falha no aviso flutuante, painel, notificação do Android e tela NVU.
   Nenhum dado é registrado nesse caso.
10. Os dois reconhecedores OCR são serializados para reduzir pico de memória e
    corrida de callbacks em aparelhos mais fracos.
11. O painel de diagnóstico GTO foi retirado de uma condição inalcançável e agora
    aparece durante a operação ativa, inclusive com a causa da confirmação recusada.

## Matriz automatizada

Os detectores de lista e resultado foram exercitados em:

- 854×480;
- 1280×720;
- 1536×691;
- 1600×900;
- 1920×1080;
- 2400×1080.

Foram cobertas listas de 1 a 6 fretes, regiões laranja irregulares de gameplay,
divergência de uma letra, perda de acento, divergência de km/valor, duplicação da
mesma passagem OCR, consenso do resultado e imutabilidade após confirmação.

A certificação completa executou 374 verificações e terminou em 374/374. Ela
incluiu TypeScript, sintaxe Java, contrato APK/backend, captura, seleção, estado,
recuperação, resultado, tamanhos de tela e paridade do runtime Web/nativo. O
fallback Capacitor também foi comparado ao `dist`: 62 arquivos idênticos por
SHA-256, além dos dois arquivos de bridge Capacitor esperados.

## Limite da auditoria automatizada

As matrizes verificam cálculos de geometria, estados, memória concorrente e
integridade dos dados em código. Este ambiente não possui um aparelho Android
físico nem o Android SDK configurado para produzir um APK assinado. Portanto, a
validação final em um celular fraco real ainda deve confirmar: lista com 1, 3 e 6
fretes; notificação aberta sobre a lista; bolinha arrastada para a direita; rotação
ou mudança de resolução; resultado normal e resultado com bônus de vídeo.

## Publicação obrigatória

Esta entrega altera o runtime Web e o código nativo:

1. faça o deploy manual da pasta `dist` no Netlify;
2. gere um APK/AAB 1.0.40 assinado com a mesma chave da versão instalada;
3. instale por atualização (`adb install -r`) ou desinstale a antiga e instale a
   nova sabendo que a desinstalação apaga os dados locais e permissões do app.

O Google AI Studio não executa o APK nem substitui o deploy. Atualize-o apenas
para manter a cópia-fonte do projeto sincronizada com esta R3.23.
