# Correção definitiva do fluxo interno de notificações

## Causas raiz confirmadas

1. Os quatro eventos principais não criavam notificações: nova operação, inscrição no RH, solicitação de trabalho e conclusão da operação.
2. O popup interno não estava implementado; havia apenas o sino.
3. `AppContext` e `NotificationContext` mantinham listeners independentes, criando estados divergentes.
4. O merge anterior preservava a versão antiga do documento e ignorava atualizações como “marcar como lida”.
5. A migração criou duas coleções (`notifications` e `notificacoes`) sem fallback consistente.
6. `firebase.json` apontava para `firestore.rules.v6`, um arquivo restritivo que não continha as regras do aplicativo.

## Correções implementadas

- `AppContext` tornou-se a única fonte de notificações internas.
- Leitura compatível das coleções `notifications` e `notificacoes`.
- Substituição integral de cada snapshot, incluindo updates e deletes.
- Filtro por `userId`, perfil ativo e empresa ativa.
- Popup interno Sonner com botão fechar, gesto de dispensar e ação “Ver”.
- Criação de notificações nos eventos:
  - `NEW_OPERATION` para motorista;
  - `RH_APPLICATION` para proprietário e administradores;
  - `WORK_REQUEST` para proprietário e administradores;
  - `OPERATION_COMPLETED` somente ao finalizar a operação completa.
- Resolução de todos os administradores ativos via `companyMembers`, além do proprietário.
- Escrita preferencial em `notifications` com fallback automático para `notificacoes` quando a coleção moderna estiver bloqueada.
- Payload moderno e legado no mesmo documento (`title/titulo`, `message/mensagem`, `read/lida`).
- Correção das regras Firestore e do arquivo indicado por `firebase.json`.

## Validações executadas

Executado em instalação limpa:

- `npm ci`: aprovado;
- `npm run lint`: aprovado;
- `npm run test:notifications-all`: aprovado;
  - motorista x corporativo;
  - usuário com dois perfis;
  - normalização legado/moderno;
  - fallback de persistência;
  - presença dos quatro produtores e do popup;
- `npm run build`: aprovado.

O teste final no Firebase real depende das credenciais e dos dados do projeto, que não ficam disponíveis neste ambiente. O fallback para a coleção legada foi mantido justamente para funcionar mesmo antes da publicação das regras novas.
