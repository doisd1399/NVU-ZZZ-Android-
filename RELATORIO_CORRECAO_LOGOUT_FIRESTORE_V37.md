# Relatório — correção segura do erro no logout (v37)

## Diagnóstico

O erro exibido pelo preview do Google AI Studio ocorre numa janela de corrida:
`signOut(auth)` revoga a sessão enquanto callbacks assíncronos ainda podem
estar ativos. Além dos listeners já controlados pelo `AppContext`, havia
listeners/cache de histórico e ranking, migrações iniciadas no boot, e o
callback de token do Push Capacitor mantendo o contexto do usuário anterior.

Quando uma dessas tarefas recebia `permission-denied` depois da revogação,
alguns caminhos usavam `console.error`. O iframe do AI Studio interpreta esse
erro esperado de encerramento como **“1 error running the code”**, embora a
tela volte para o login.

## Correção aplicada

- Criado `src/lib/authLifecycle.ts` (e a cópia `nvu26/`) para sinalizar o
  início do teardown antes de qualquer `signOut`.
- `logOutApp` agora dispara o sinal, cancela listeners, limpa estado privado e
  contexto de push, aguarda a desmontagem do React e só então executa
  `signOut(auth)`.
- Hooks de histórico/ranking, perfil de motorista e painel sênior cancelam
  listeners, timers, retries e caches ao receber o sinal.
- Migrações e sincronizações verificam o sinal antes e depois de operações
  assíncronas; erros esperados durante o teardown não são reportados como
  falhas de execução.
- O Push Capacitor mantém o plugin nativo e seus listeners, mas invalida o
  contexto/token anterior e só grava no Firestore quando o UID atual ainda
  corresponde ao contexto. Isso preserva o fluxo do APK e impede gravação
  tardia na conta errada.
- As duas árvores (`src/` e `nvu26/src/`) foram mantidas sincronizadas.

Não foram alteradas regras do Firestore, dados, ranking, métricas ou o
identificador do aplicativo Capacitor.

## Validação

- teste de regressão da ordem de logout: aprovado;
- teste do bridge de teardown: aprovado;
- build Vite da árvore raiz: aprovado (3.291 módulos);
- build Vite da árvore `nvu26`: aprovado (3.291 módulos).

Os builds exibem apenas avisos já existentes de tamanho de chunks/imports. A
confirmação final do aviso visual deve ser feita no preview real: entrar,
abrir uma tela em tempo real, sair e entrar novamente.
