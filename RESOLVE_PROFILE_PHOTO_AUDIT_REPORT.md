# Relatório de Auditoria e Correção: Perfil Geral e Fotos

## Causa Raiz Identificada
As falhas de compilação recentes ocorreram devido a duas questões em relação à implementação do `resolveDriverPhoto`:
1. **Erro Sintático**: Um erro de digitação duplicou a palavra-chave `import {` em `SelectProfile.tsx` e `AdminLayout.tsx` ao introduzir `resolveDriverPhoto`. Além disso, um `\n\n` literal vazou em `RegisterCompany.tsx`.
2. **Uso Incorreto do Resolvedor**: A função `resolveDriverPhoto` (focada unicamente em motoristas) foi injetada em componentes globais de contexto de aplicação (`SelectProfile`, `AdminLayout`, `ProfileModal`), que manipulam e representam um "Usuário Geral" ou perfis empresariais genéricos, e não apenas Motoristas. Além disso, a implementação anterior do `resolveDriverPhoto` esqueceu-se de adicionar `profilePhotoURL`, o campo canônico primário, como a primeira propriedade de fallback.

## Ação e Resolução
1. **Limpeza e Fix Sintático**: Todos os erros de sintaxe (imports e newlines) introduzidos durante o patch de `resolveDriverPhoto` foram corrigidos e o build agora está verde.
2. **Nova Abstração (`resolveProfilePhoto`)**:
   - Criamos o arquivo `src/lib/resolveProfilePhoto.ts` para servir como a fonte unificada de renderização de imagem de perfis globais e companhias, agregando não só chaves de avatar, mas também `logoUrl`, `companyLogoURL` e `ownerPhotoUrl`.
   - Substituímos o uso de `resolveDriverPhoto` nesses 3 componentes globais pelo novo `resolveProfilePhoto`.
3. **Inclusão do `profilePhotoURL`**: 
   - A função `resolveDriverPhoto.ts` foi atualizada para incluir `profilePhotoURL` no topo da precedência, garantindo que a foto principal nunca seja ignorada, alinhando com a correção já validada no módulo de Ranking.

## Arquivos Alterados
- `src/lib/resolveProfilePhoto.ts` (Criado)
- `src/lib/resolveDriverPhoto.ts` (Corrigido)
- `src/pages/SelectProfile.tsx` (Resolvido import e adotado `resolveProfilePhoto`)
- `src/layouts/AdminLayout.tsx` (Resolvido import e adotado `resolveProfilePhoto`)
- `src/components/ProfileModal.tsx` (Adotado `resolveProfilePhoto`)
- `src/pages/RegisterCompany.tsx` (Limpeza sintática de `\n`)

## Restrições Preservadas
- `simulatorId` intacto.
- Rotina e mecânica de cálculo de `ranking` intocadas.
- Lógicas de `histórico` e `operações` perfeitamente alinhadas.
