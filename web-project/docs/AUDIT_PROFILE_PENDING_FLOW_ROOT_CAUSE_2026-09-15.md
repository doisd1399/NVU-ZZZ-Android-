# Auditoria do fluxo Perfil aprovado × Pendências

## Escopo

A auditoria verificou o botão `Escolher perfil`, o resolver de inicialização, o índice canônico de perfis, as rotas `/select-profile`, `/pending-applications` e `/status`, além dos destinos após envio de inscrição de motorista e cadastro de empresa. Nenhuma imagem anexada foi reaberta.

## Evidências confirmadas

1. `src/pages/Portal.tsx` define `hasRestoredIdentity` como `Boolean(currentUser) || Boolean(sessionUiReady)`. Ao clicar no botão, qualquer identidade restaurada navega diretamente para `/select-profile` com `nvuExplicitProfileSelection`, sem consultar `profileIndex` nem pendências. Portanto, uma conta autenticada sem perfil aprovado também abre o seletor.

2. `src/services/profileIndex.ts` é a autoridade correta para perfis aprovados: retorna `status: "ready"` somente quando há pelo menos um perfil válido derivado de membership ativa e contexto de empresa/simulador resolvido. Sem membership aprovada, retorna `status: "empty"` ou `"error"`; candidaturas pendentes não são perfis.

3. `src/App.tsx` permite a rota `/select-profile` explicitamente e o resolver aceita a intenção `nvuExplicitProfileSelection` sem verificar se `profileIndex.profiles.length > 0`. Isso mantém a rota aberta mesmo quando a conta não tem perfil aprovado.

4. `src/pages/SelectProfile.tsx` renderiza uma caixa de estado vazio dentro do próprio seletor. Quando existem pendências, ela ainda usa o título `Aguardando aprovação!`; isso contradiz a exigência de usar a superfície única `Pendências`.

5. `src/pages/PendingApplications.tsx` já é a superfície dedicada para inscrições/cadastros pendentes, mas seu botão de retorno envia para `/select-profile`, e seu estado sem pendências não possui as ações compactas solicitadas. A página precisa se tornar o destino protegido para contas sem perfil aprovado e redirecionar para o seletor somente quando surgir perfil aprovado.

6. `RecruitmentApply.tsx` e `RegisterCompany.tsx` ainda navegam para `/status` após o envio. Isso mantém uma segunda superfície de aprovação paralela, enquanto o comportamento solicitado exige `/pending-applications` imediatamente após qualquer envio.

7. `ApplicationStatus.tsx` possui lógica própria para pendente/aprovado/rejeitado e também redireciona usuários com membership ativa para `/select-profile`. Ela deve deixar de ser o destino normal pós-envio; a correção pode preservar a rota para compatibilidade histórica, mas o fluxo novo não deve depender dela.

## Causa-raiz

A causa primária do seletor indevido é uma decisão de entrada baseada apenas em identidade restaurada, não em perfil aprovado: o Portal trata `currentUser/sessionUiReady` como autorização para abrir `/select-profile`. A causa estrutural secundária é a ausência de uma barreira de rota em `/select-profile` e `/pending-applications` baseada no `profileIndex` canônico.

## Correção segura planejada

- Consultar a autoridade `profileIndex` no Portal/entrada do seletor.
- Direcionar contas autenticadas sem perfil válido para `/pending-applications`, independentemente de haver ou não pendência; a página exibirá a situação correspondente e ações compactas.
- Manter `/select-profile` exclusivamente para `profileIndex.status === "ready"` e pelo menos um perfil válido.
- Fazer `/pending-applications` redirecionar para `/select-profile` somente quando `profileIndex` estiver pronto com perfis aprovados.
- Redirecionar diretamente para `/pending-applications` após envio de motorista ou cadastro de empresa.
- Remover o título `Aguardando aprovação!` da superfície de seleção; a comunicação pendente ficará somente em `Pendências`.
- Preservar a proteção contra estado de carregamento e erro; nunca transformar falha de consulta em ausência confirmada de perfil.

## Critério de validação

O APK só será entregue depois de testes que cubram: conta com perfil aprovado abrindo seletor; conta sem perfil com pendência abrindo Pendências; conta sem perfil e sem pendência permanecendo em Pendências com mensagem adequada; envio de motorista e empresa chegando a Pendências; aprovação criando perfil e permitindo seletor; e nenhum redirecionamento para workspace sem membership ativa.
