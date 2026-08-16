# Relatório - Correção Simuladores Firebase NVU

## Arquivos alterados
- src/context/AppContext.tsx
- nvu26/src/context/AppContext.tsx (quando presente)

## Causa raiz
O AppContext possuía seed automático e fallback hardcoded de simuladores quando a coleção /simulators estava vazia ou retornava erro de permissão.

## Correção aplicada
- Removida criação automática de simuladores.
- Removidos simuladores fixos no código.
- Firebase `/simulators` tornou-se a única fonte de verdade.
- Erros de leitura agora são registrados no console e o estado permanece seguro vazio.

## Preservado
- Ranking
- Histórico de viagens
- Operações
- Notificações
- simulatorId

## Validação esperada
Após publicação das Rules:
- Cadastro de empresa lê simuladores reais do Firestore.
- Inscrição de motorista lê simuladores reais do Firestore.
- Novos simuladores criados no Firebase aparecem automaticamente.
