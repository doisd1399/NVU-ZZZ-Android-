# Relatório — Correção das imagens no Painel Sênior

## Causa raiz confirmada

O formulário de cadastro processava a imagem corretamente no preview, mas no envio dependia exclusivamente do Firebase Storage. Havia um timeout artificial de 8 segundos. Qualquer lentidão, ausência de regra de Storage ou falha de autenticação era capturada e convertida silenciosamente em string vazia. Mesmo assim, o documento era criado em `recruitment_applications`.

Por isso o Painel Sênior recebia exatamente `companyLogoURL: ""` e `ownerPhotoUrl: ""` e exibia “Não enviada”. O componente do Painel Sênior não era a origem principal do desaparecimento.

## Correção aplicada

- Compressão limitada a 420 px e aproximadamente 280 KB por imagem.
- Tentativa de upload no Storage com timeout de 25 segundos.
- Fallback automático para data URL compactada no próprio documento do Firestore se o Storage falhar.
- O cadastro não perde mais a imagem silenciosamente.
- Normalização no Painel Sênior para campos atuais e nomes legados de logo/foto.
- Aprovação continua propagando a imagem normalizada para a empresa e para o perfil do proprietário.

## Arquivos alterados

- `src/pages/RegisterCompany.tsx`
- `src/pages/admin/SeniorPanel.tsx`
- `src/lib/registrationImages.ts`
- espelho equivalente em `nvu26/src/`

## Limitação de dados anteriores

Solicitações já salvas com ambos os campos vazios não possuem a imagem no Firestore. O código não consegue reconstruir arquivos que foram descartados antes da gravação. Essas solicitações precisam ser reenviadas após a implantação da correção.
