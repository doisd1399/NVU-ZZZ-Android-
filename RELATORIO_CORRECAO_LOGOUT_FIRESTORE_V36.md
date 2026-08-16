# RELATÓRIO — CORREÇÃO SEGURA DO LOGOUT / FIRESTORE

## Arquivo-base

`nvu-zzz (35).zip`

## Sintoma

Ao tocar em **Sair**, a interface voltava corretamente para a tela de login, mas o Google AI Studio registrava **“1 error running the code”**.

## Causa raiz confirmada no código

A função `logOutApp()` executava `signOut(auth)` antes de desmontar os listeners privados do Firestore. Durante a pequena janela entre a revogação da autenticação e a desmontagem dos componentes, listeners de usuário, vínculos, notificações e dados operacionais ainda podiam tentar ler coleções protegidas e receber `permission-denied`.

O preview incorporado do Google AI Studio trata alguns `console.error()` desse intervalo como erro de execução, mesmo quando a navegação para o login termina corretamente.

Também foram encontradas duas inconsistências relacionadas ao encerramento de sessão:

- o estado de acesso sênior não era limpo explicitamente;
- `membershipsLoaded` não era reiniciado ao anexar o listener de um novo usuário, podendo reutilizar um estado antigo durante um login seguinte.

## Correção aplicada

### Ordem segura de logout

O fluxo agora é:

1. bloquear toques duplicados no botão de saída;
2. cancelar listeners autenticados registrados pelo `AppContext`;
3. limpar o usuário e o contexto privado da interface;
4. aguardar um ciclo de renderização para desmontar listeners das páginas;
5. executar `signOut(auth)`;
6. finalizar o estado de logout.

### Listeners tratados

- documento `users/{uid}`;
- `companyMembers` do usuário;
- veículos;
- reboques;
- contratos;
- sequências;
- trabalhos;
- demandas;
- usuários da empresa;
- membros da empresa;
- solicitações;
- inscrições;
- notificações das coleções `notifications` e `notificacoes`.

Os cancelamentos foram tornados idempotentes para que a limpeza direta e a limpeza normal do React possam ocorrer sem conflito.

### Proteção dos callbacks

Callbacks de erro dos listeners autenticados agora ignoram erros esperados quando:

- o logout está em andamento; ou
- o Firebase Auth já não possui usuário autenticado.

Erros reais fora do logout continuam sendo registrados normalmente.

### Estado limpo ao sair

Também são limpos:

- `activeCompanyId`;
- `activeRole`;
- acesso sênior;
- empresa sênior;
- vínculos e indicador de carregamento;
- usuários e membros;
- veículos, reboques, contratos e sequências;
- trabalhos e demandas;
- solicitações, inscrições e notificações;
- contexto de push do usuário.

## Arquivos alterados

- `src/context/AppContext.tsx`
- `nvu26/src/context/AppContext.tsx`
- `test_logout_teardown_regression.ts`

Não foram alterados:

- `simulatorId`;
- ranking;
- histórico;
- operações;
- métricas;
- fotos/avatar;
- regras de negócio;
- regras do Firestore.

## Validações realizadas

- `tsc --noEmit`: aprovado;
- teste específico da ordem de logout: aprovado;
- testes completos de notificações: aprovados;
- teste de regressão de login/notificações: aprovado;
- teste de foto do proprietário após aprovação: 3/3 aprovado;
- build Vite/Esbuild: aprovado;
- 3.292 módulos transformados;
- arquivos raiz e `nvu26/` sincronizados.

O build apresentou apenas avisos já existentes sobre tamanho de chunk e importações estáticas/dinâmicas; nenhum deles bloqueia a compilação e não foi introduzido por esta correção.

A pasta `dist/` foi gerada temporariamente para validar a compilação, mas não foi adicionada ao ZIP final porque o arquivo-base do desenvolvedor não continha artefatos de build. Assim, o pacote mantém a mesma estrutura de código-fonte do original.

## Teste manual recomendado no ambiente real

1. entrar em um perfil administrador;
2. abrir uma tela com dados em tempo real;
3. tocar em **Sair**;
4. confirmar que a tela de login abre;
5. confirmar que o Google AI Studio não registra novo erro;
6. entrar novamente e confirmar que vínculos, empresa e perfil carregam normalmente.

A validação automatizada confirma a ordem de desmontagem e a compilação. A confirmação definitiva do aviso visual depende do Firebase e do iframe reais do Google AI Studio.
