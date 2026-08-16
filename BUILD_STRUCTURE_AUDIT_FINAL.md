# Auditoria de estrutura de build NVU v9

## Resultado
O build oficial utiliza a raiz do projeto.

Fluxo confirmado:

package.json raiz
-> vite.config.ts raiz
-> src/
-> dist/
-> Capacitor webDir dist

## Capacitor
capacitor.config.ts aponta:
webDir: dist

## Pasta nvu8
A pasta nvu8 contém uma cópia/área de suporte, porém não é o diretório usado pelo build principal.

Foram encontrados testes na raiz que importam arquivos de nvu8:
- test_profile_ranking_alignment_nvu8.ts
- test_ranking_page_modes_nvu8.ts

Por segurança, ela NÃO deve ser removida automaticamente.

## Correção aplicada
- Mantido build oficial na raiz.
- Adicionado este relatório de confirmação.
- Não removidos arquivos para evitar quebra de testes existentes.
- Nenhuma alteração no fluxo Vite/Capacitor/Firebase.
