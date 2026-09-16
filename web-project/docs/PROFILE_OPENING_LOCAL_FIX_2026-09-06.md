# Correção local — abertura imediata do perfil

## Causa arquitetural identificada

O caminho `SelectProfile → handleSelect → commitProfileNavigation → switchRole → navigate` já era síncrono do ponto de vista da interação: `switchRole` valida o membership em memória, atualiza `activeRole`/`activeCompanyId` e grava a persistência em background; `navigate` não era aguardado.

O atraso perceptível vinha principalmente da fronteira de carregamento do destino. `RouteWarmup` iniciava os imports dos dois destinos primários (`/admin/fleet` e `/driver/profile`) em `useEffect`, ou seja, somente depois da primeira pintura do seletor. Se o usuário tocasse antes de os chunks terminarem, os layouts e páginas lazy entravam em `Suspense` e o fallback `RouteLoading` cobria o destino até a resolução dos imports.

A confirmação de membership e o OTA não foram encontrados como `await` dentro do clique. A autorização continua sendo verificada pelo `ProtectedRoute`; o Live Update continua fora do caminho crítico.

## Correção aplicada

A correção local foi mínima:

1. O preload best-effort dos dois destinos principais foi movido de `useEffect` para `useLayoutEffect` em `src/App.tsx`. Isso inicia a preparação dos chunks antes da primeira pintura do seletor, sem aguardar o preload, sem criar delay e sem torná-lo requisito obrigatório.
2. O `ProtectedRoute` passou a reconhecer explicitamente o caso em que o usuário acabou de selecionar um perfil `ready` do Profile Index, com UID, role, company e `ActiveProfileContext` coerentes. Essa exceção somente evita que `sessionUiReady` cubra o primeiro paint quando a seleção canônica já existe; as verificações posteriores de membership ativa, role, empresa e owner continuam intactas.
3. Perfil não canônico, role divergente, company divergente, usuário ausente ou Profile Index não pronto continuam seguindo o guard normal e não ganham acesso.

Não foi criado pending intent, hydration artificial, polling, timeout, cache novo, fallback de identidade ou navegação antecipada.

## Segurança

A correção não remove `ProtectedRoute`, `sessionUiReady`, validação de role, membership ativa, company ativa, owner check, Firebase Auth ou regras Firestore. O fast path depende simultaneamente de `currentUser.id`, `profileIndex.uid`, `profileIndex.status === "ready"`, `activeProfileContext`, `activeRole` e `activeCompanyId` coerentes. Mesmo nesse caminho, a validação de acesso específica da rota continua sendo executada.

## Testes

Passaram após a correção:

| Teste | Resultado |
| --- | --- |
| `npm run lint` | PASS |
| `npm run build` | PASS |
| `npm run verify:release` | PASS |
| Auth architecture decoupling | PASS 14/14 |
| Profile Index | PASS 5 cenários |
| Profile fast access | PASS 12 checks |
| Auth instant path | PASS 16 checks |
| Workspace shell fast | PASS 13 checks |
| Login/profile recovery | PASS |
| Session stability/projection | PASS |
| Membership repository contract | PASS 13/13 |
| OTA manager contract | PASS 17/17 |
| Live Update status | PASS |

O build aprovado continua declarando Web 2.3.52. Os avisos de chunk grande do Vite permanecem apenas como warning de empacotamento e não interromperam o gate.

## Limites

Esta é uma correção **local בלבד**. Não foi publicada Web, não foi publicado OTA, não foi alterado `production-286`, não foi alterado Android/Capacitor/Gradle, não foi alterado versionCode/versionName e nenhum APK foi gerado.

A aplicação em dispositivo físico não foi declarada comprovada nesta etapa, pois o navegador remoto não permitiu uma sessão autenticada funcional e não houve instrumentação executada no WebView do HF208. A instrumentação T0–T11 permanece disponível separadamente para medir o comportamento em uma sessão autenticada controlada.

## Decisão

**Correção local validada estruturalmente.** O próximo passo, se autorizado, é um teste funcional em um ambiente autenticado que execute exatamente esta Dist. Nenhum release deve ser publicado a partir deste relatório sem essa decisão explícita.
