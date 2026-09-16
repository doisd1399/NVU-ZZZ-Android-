# Auditoria — Edição de valores Senior após OTA 2.3.142

## Conclusão

A OTA contém a implementação de edição de valores. A causa raiz identificada foi uma inconsistência entre duas fontes de autorização Senior:

1. `App.tsx` e o contexto global usam a sessão Senior validada pelo Firebase (`isSeniorAuthenticated` + `seniorCompanyId`).
2. `TripHistory.tsx` calculava `isSeniorAccess` exclusivamente com `sessionStorage.getItem("seniorAccess") === "true"`.

A chave `sessionStorage.seniorAccess` só era gravada no fluxo `SeniorPanel.viewCompanyProfile()`. Assim, um usuário autenticado e autorizado como Senior podia abrir o histórico com `isSeniorAccess=false`, ocultando o campo `Valor recebido` e enviando apenas origem/destino ao salvar.

## Correção aplicada

`TripHistory` agora consome `isSeniorAuthenticated` do `SessionStore` como fonte principal de autorização. A chave legada `seniorAccess` permanece somente como compatibilidade temporária do preview de empresa. As regras do Firestore continuam sendo a autoridade final para qualquer gravação.

## Verificações da auditoria

| Verificação | Resultado |
|---|---|
| Sessão Senior validada consumida por `TripHistory` | Corrigido |
| Compatibilidade com preview de empresa | Preservada |
| Campo e persistência de `valor`/`valorCents` | Preservados |
| Marcação `valorEditadoNoPainelSenior` | Preservada |
| Regressão automatizada | Adicionada em `scripts/test-senior-trip-history-auth.mjs` |

## Limite

A autorização final continua dependente das regras Firestore e deve ser validada em dispositivo/ambiente de homologação com uma sessão Senior real antes da distribuição do APK.
