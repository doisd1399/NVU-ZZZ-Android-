# Auditoria e implementação — Desktop e remoção imediata da frota

**Data:** 16 de setembro de 2026  
**Escopo:** experiência desktop do painel administrativo e atualização visual após remoção de funcionário.

## Achados

A navegação principal do shell administrativo estava acionável apenas pelo botão mobile (`lg:hidden`), embora o menu lateral fosse renderizado para desktop. Em resoluções desktop, isso deixava as opções da aplicação ocultas no cabeçalho.

A página da frota também aplicava `max-w-3xl` fora do perfil da empresa. Esse limite estreito fazia o banner ocupar uma proporção excessiva da página e deixava os cards de funcionários largos e esticados. O banner tinha 320 pixels no breakpoint `sm`, sem uma composição própria para monitores largos.

A remoção de funcionário já era confirmada no Firestore, mas as projeções locais usadas pelos cards aguardavam o próximo snapshot para desaparecer.

## Implementação

O botão de menu do cabeçalho agora permanece disponível no desktop e alterna entre os ícones de menu e fechamento. O mesmo estado continua sendo usado pelo menu lateral existente, sem alterar o fluxo mobile.

A página da frota passou a usar largura controlada de até 1440 pixels em desktop, com espaçamento lateral adequado. O banner reduz sua altura para 248 pixels em telas grandes e se estende corretamente até as margens do conteúdo.

Os cards de funcionários passaram a utilizar duas colunas em desktop, três colunas em telas `xl` e quatro em telas `2xl`. O espaçamento e o padding desktop foram reduzidos, preservando as dimensões originais em mobile.

Após o commit confirmado da remoção, `allCompanyMembers`, `users` e `fetchedMissingUsers` são atualizados imediatamente. Os snapshots permanecem responsáveis pela reconciliação posterior.

## Validação

| Verificação | Resultado |
|---|---:|
| Contrato de layout desktop | **6/6 PASS** |
| Regressão de remoção imediata | **5/5 PASS** |
| TypeScript | **PASS** |
| Build Web e manifesto | **PASS** |

## Deploy

A `dist` foi gerada localmente pelo build de produção. O deploy público Web/OTA depende do conector Netlify, que está configurado na sessão, mas desabilitado; nenhum deploy externo foi iniciado sem a habilitação desse conector. O APK nativo não é regenerado por uma alteração exclusivamente Web até que o bundle seja incorporado ao projeto Capacitor ou publicado pelo canal OTA configurado.
