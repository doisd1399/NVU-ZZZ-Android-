# Relatório — Foto do proprietário após aprovação

## Causa raiz

A imagem era enviada e permanecia disponível na solicitação, mas o fluxo de login executava uma unificação de documentos que gravava `profilePhotoURL: null` em todas as autenticações. Isso apagava a foto propagada pelo Painel Sênior. O mesmo trecho também lia `user.profilePhotoURL`, propriedade inexistente no usuário do Firebase Auth; a propriedade correta é `user.photoURL`.

Havia ainda dois riscos secundários:

1. Em contas com documentos duplicados por e-mail, a unificação escolhia um documento pelo nome e podia ignorar a foto existente em outro documento.
2. Contas já afetadas permaneciam sem foto no documento `users`, embora a inscrição aprovada ainda guardasse `ownerPhotoUrl`.

## Correções aplicadas

- O login preserva a foto NVU existente durante a unificação de documentos.
- `authPhotoURL` passa a usar corretamente `FirebaseUser.photoURL` e permanece separado de `profilePhotoURL`.
- A unificação procura a foto em todos os documentos duplicados antes de removê-los.
- O AppContext recupera uma única vez a foto de uma inscrição empresarial aprovada quando o usuário atual está sem foto.
- A aprovação no Painel Sênior normaliza as imagens antes de propagá-las e registra se a foto foi propagada.
- O ProfileModal não volta a apagar visualmente a foto ao sincronizar o usuário.

## Arquivos alterados

- `src/pages/Login.tsx`
- `src/context/AppContext.tsx`
- `src/pages/admin/SeniorPanel.tsx`
- `src/components/ProfileModal.tsx`
- `src/lib/profilePhotoRecovery.ts`
- `test_profile_photo_approval_regression.ts`

Os equivalentes em `nvu26/` foram sincronizados para evitar divergência no Google AI Studio.

## Escopo preservado

Não foram alterados simulatorId, ranking, histórico, operações, métricas, aprovação de motoristas ou regras operacionais.
