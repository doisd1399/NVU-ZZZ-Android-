# Correção regressão fotos de perfil

## Causa raiz
Alguns componentes ainda consumiam diretamente `profilePhotoURL`, enquanto o Ranking utilizava fallback de resolução.

## Arquivos alterados
- src/pages/SelectProfile.tsx
- src/layouts/AdminLayout.tsx
- src/components/ProfileModal.tsx

## Correção
Aplicado `resolveDriverPhoto()` como fonte única para avatar.

## Preservado
- simulatorId
- ranking
- histórico
- operações
- regras de negócio
