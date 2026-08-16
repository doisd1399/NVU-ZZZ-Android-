# Correção das fotos de candidatos — Recursos Humanos

## Causa encontrada

`RecruitmentTab` calculava `userMatch`, mas não usava o perfil encontrado. Quando
`applicationPhotoURL` estava vazio, a tela montava uma URL de avatar para um
domínio inexistente (`ui-profilePhotoURLs.com`). O navegador tentava carregar
essa URL e mostrava o ícone de imagem quebrada, como nas telas enviadas.

## Correção aplicada

- A foto da inscrição é resolvida primeiro (`applicationPhotoURL` e aliases
  legados).
- Se a inscrição não tiver foto, o perfil canônico do candidato é procurado por
  UID e por e-mail normalizado; candidatos pendentes também são lidos
  diretamente em `users/{userId}`.
- Caminhos antigos do Firebase Storage (`gs://...` ou `empresas/...`) são
  convertidos em URLs de download antes da renderização.
- Se uma URL antiga estiver quebrada, a tela tenta a próxima fonte válida (por
  exemplo, a foto de perfil canônica) antes de usar o fallback local.
- A renderização usa `StableImage` e um fallback local com iniciais. Nenhum
  domínio externo de avatar é usado, portanto não aparece mais imagem quebrada.
- O upload da foto do candidato recebeu uma margem de 30 segundos e metadados
  explícitos de imagem/cache para reduzir perdas em conexões móveis.

## Validação

- `npm run lint` — aprovado
- `npm run test:recruitment-photo` — aprovado
- `npm run build` — aprovado

As regras de aprovação, rejeição e os dados operacionais não foram alterados.
