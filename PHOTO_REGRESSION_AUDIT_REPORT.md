# Auditoria regressão fotos motoristas

## Causa raiz identificada

A regressão ocorreu no mapeamento do ranking. O componente recebia os usuários corretamente, porém o engine de montagem dos cards utilizava somente:

user.profilePhotoURL

Quando os motoristas possuíam fotos salvas em campos legados diferentes, o valor retornava vazio.

## Correção aplicada

Arquivo:
src/lib/rankingPageEngine.ts

Alterado o resolvedor de imagem para aceitar fallback:

- profilePhotoURL
- photoURL
- photoUrl
- avatar
- profileImage
- imageUrl
- photo

## Preservado

- simulatorId
- ranking
- histórico
- CompanyMembers
- operações

A correção atua apenas no mapeamento visual da foto.
