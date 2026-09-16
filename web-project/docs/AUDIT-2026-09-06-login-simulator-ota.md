# Auditoria login, simulador e OTA — 2026-09-06

## Escopo e restrição

A auditoria está sendo executada sem gerar APK e sem publicar deploy. As imagens enviadas pelo usuário não serão reabertas por ferramenta de arquivo; as evidências visuais já observadas servem apenas como contexto.

## Evidências iniciais — SelectProfile

`SelectProfile` deriva `availableCompanies` diretamente de memberships ativos. O rótulo exibido combina `companyName` com `resolveSimulatorDisplayLabel(company, simulators, allCompanies)`, quando há documento de empresa correspondente; se a empresa ainda não foi hidratada, usa apenas o nome da membership. O estado de seleção visual pode aparecer antes da autorização canônica (`sessionUiReady`/`membershipsUiReady`).

O clique em um perfil não navega imediatamente se `sessionReady` ainda for falso. Ele grava `pendingProfileIntentRef`, exibe estado pendente apenas para leitores de tela e chama `refreshSession()`. A navegação só ocorre no effect que espera `sessionReady`, após `commitProfileNavigation`, que chama `switchRole` e navega para `/admin/fleet` ou `/driver/profile`.

Isso é uma possível causa direta do bloqueio no caminho login → seletor: a superfície pode estar visualmente pronta, mas o primeiro clique é deliberadamente mantido em intenção pendente até a confirmação de memberships. Ainda é necessário verificar se o bloqueio observado é apenas essa espera de autorização ou se há também overlay/rota de loading.

## Próximas verificações

1. Confirmar a autoridade de simuladores e como um simulatorId/name não registrado pode entrar no estado de empresa/seleção.
2. Confirmar se o primeiro clique no seletor é rejeitado, adiado ou navega para uma combinação de role/simulador sem vínculo.
3. Verificar a causa comum da ausência de aviso OTA na Web e no Android, distinguindo renderização do banner, execução do manager e estado current/next bundle.

## Evidência do resolver de simuladores

`resolveSimulatorId` constrói grupos com simulators e companies, mas, quando não encontra um grupo correspondente, não rejeita o valor: para string retorna `normalizeSimulatorId(data)` e para objeto retorna `explicitId` ou o nome legado normalizado. Portanto, uma seleção/registro contendo um ID ou nome que não existe no catálogo ainda pode ser tratado como simulador válido. Isso é uma causa comprovada de identidade órfã; ainda é necessário localizar o ponto que cria a opção/seleção e decidir a barreira correta para rejeitar somente valores não registrados sem quebrar compatibilidade legada.

O resolver também usa `pickCanonicalId` que pode escolher alias semântico quando não há ID de catálogo, reforçando a possibilidade de um simulador textual entrar no fluxo como se tivesse identidade canônica.

## Evidência do catálogo e da janela de login

O AppContext suspende a assinatura da coleção `simulators` quando `interactionFirstRoute` é verdadeiro, incluindo `/select-profile`. Essa decisão reduz trabalho durante o clique, mas deixa o seletor sem uma atualização do catálogo canônico nessa rota; o array anterior pode estar vazio ou desatualizado.

`buildSimulatorSelectorOptions` cria grupos tanto da coleção `simulators` quanto de `companies`. Uma company com `simulatorId`/`simulatorName` pode gerar uma opção mesmo quando não existe registro correspondente na coleção `simulators`; aliases de empresa são tratados como uso válido. Isso pode explicar a abertura de um simulador não registrado: a empresa/membership fornece identidade textual e o catálogo canônico não está presente ou não foi validado.

O código não cria simuladores no listener, mas a combinação `companies + resolver fallback + suspensão do catálogo em /select-profile` permite que a UI continue apresentando um simulador órfão como se fosse registrado.

## Evidência do gate login → seletor → perfil

`ProtectedRoute` espera `sessionUiReady`, não `sessionReady`, para renderizar a superfície protegida; depois verifica `activeCompanyId`, `activeRole` e membership ativa. Já `SelectProfile.handleSelect` só chama `commitProfileNavigation` quando `sessionReady` é verdadeiro. Antes disso, o toque vira `pendingProfileIntentRef` e chama `refreshSession`, sem feedback visual para o usuário além de um texto `sr-only`. Isso comprova uma espera silenciosa no primeiro clique.

O redirecionamento inicial de `AppRouteContent` escolhe a home de `activeRole` ou `/select-profile` quando a conta é restaurada. Como `activeRole` e vínculo são estados independentes que podem chegar em momentos diferentes, a rota pode ser montada enquanto memberships/simuladores ainda estão em reconciliação. O caminho de perfil protegido depois pode redirecionar de volta ao seletor caso `activeMembership` ainda não esteja disponível.

Ainda não há base segura para remover a verificação canônica: isso poderia abrir uma rota sem autorização. A causa comprovada até aqui é a espera silenciosa de `sessionReady` após o primeiro clique, combinada com a possibilidade de entrar na superfície antes da reconciliação terminar.

## Evidência OTA Web x Android

`OtaManager.start()` e `check()` retornam imediatamente quando `Capacitor.isNativePlatform()` é falso. Portanto, a versão Web aberta em navegador não executa a OTA do Capawesome e não deve exibir a mensagem de atualização; o site Web recebe publicação normal, não atualização Live Update. A expectativa de aviso na Web é incompatível com o contrato atual.

No Android, o manager valida manifesto e publica estados `checking`, `available`, `downloading`, `verifying`, `staged`, `completed` ou `failed`. O diagnóstico completo é persistido em `localStorage`, mas `LiveUpdateStatus` renderiza somente `downloading`, `verifying`, `staged` e `completed`; falhas de manifesto, canal, runtime, assinatura, checksum ou download ficam silenciosas na UI. Se o plugin retornar current/next bundle igual ao manifesto, o manager publica `idle`, também sem aviso.

Isso explica por que a ausência de mensagem na Web não prova falha: é uma exclusão por desenho. Para o Android ainda é indispensável o diagnóstico persistido do HF208 para saber se houve `OTA_MANIFEST_UNAVAILABLE`, mismatch, `OTA_ALREADY_STAGED` implícito ou ausência de execução nativa. Sem esse diagnóstico, não é possível afirmar 100% a causa específica do Android.

## Reconciliação de memberships e bloqueio no primeiro clique

O gate confirma que `membershipsUiReady`/cache pode pintar o seletor, mas `sessionReady` só fica verdadeiro com snapshot canônico da sessão atual (`membershipsLoaded`). Em primeiro login ou reautenticação, um snapshot vazio fica pendente enquanto a reconciliação de identidade ocorre; há um timer de recuperação de 12 segundos. Em erro do listener, memberships antigas podem continuar visíveis apenas para apresentação, enquanto `membershipsLoaded=false` e `sessionRecovering=true` mantêm ações protegidas bloqueadas.

Essa arquitetura explica exatamente o sintoma de cards visíveis com primeiro clique sem efeito e sem mensagem: `SelectProfile` registra a intenção em memória e aguarda `sessionReady`, mas não apresenta um feedback visual textual. O atraso não é necessariamente um overlay; pode ser a barreira canônica de autorização.

Não é seguro remover essa barreira sem substituir a fonte de autorização por um snapshot confirmado da mesma UID. A correção correta precisa tornar o estado pendente explícito e não perder o primeiro clique, sem permitir abrir um perfil antes de `membership.status=active` e do role correspondente.

## Autenticação e repetição observada no console

A busca encontrou uma única chamada `onAuthStateChanged` no AppContext e uma única `restoreNativeGoogleSession`; portanto, não há prova de dois listeners Firebase independentes. Os logs repetidos de `Firebase user detected`/`native session restored` podem ser múltiplas gerações do mesmo effect após `sessionRefreshEpoch`, resume ou retry, e não necessariamente dois usuários.

Mesmo assim, cada geração inicia em paralelo `unifyUserDocument` com timeout de 12s, listener do documento `users/{uid}` com fallback de 1,4s, e o listener de memberships com recuperação de 12s. Durante isso `sessionRecovering=true` e `membershipsLoaded=false`; o seletor pode pintar de cache, mas o clique fica pendente até a autoridade canônica. A imagem do console confirma a conta/UID restaurada e o seletor, mas não comprova que memberships canônicas e simulator catalog já estavam prontos no instante do clique.

## Evidência do simulador não registrado no perfil

`DriverProfileIsolated` resolve a empresa somente por `viewedCompanyId`/`activeCompanyId` em `allCompanies` ou `companies`. Se nenhuma delas estiver hidratada, `resolvedCompany` é `null`. Mesmo assim, o cabeçalho calcula `resolvedSimulatorLabel` com fallback para `resolvedCompany?.simulatorName || "G. Truck"` e renderiza o perfil. Esse fallback é uma causa comprovada de aparência enganosa: o usuário pode ver “G. Truck” mesmo sem uma empresa/simulador registrado resolvido.

Além disso, `resolveCompanySimulatorFilterValue(resolvedCompany, ...)` recebe `null` nesse cenário. A página continua aberta, mas seus filtros e histórico não têm uma identidade canônica de simulador. O comportamento correto deve ser estado explícito “simulador não vinculado”/retorno ao seletor, não inventar ou presumir GTO/G. Truck.

## Login Google e origem da janela de bloqueio

`Login.handleGoogleLogin` aguarda apenas `auth.authStateReady()` e igualdade de UID, depois navega imediatamente para `/select-profile`. O serviço Google Auth confirma a credencial Firebase, mas não espera `unifyUserDocument`, memberships canônicas, empresa ou catálogo de simuladores; essa separação é intencional para não bloquear o retorno do Google.

O problema é que o seletor é apresentado antes de o estado de autorização estar pronto, mas `SelectProfile.handleSelect` mantém o primeiro clique em uma intenção silenciosa até `sessionReady`. Isso conecta diretamente o sintoma “bloqueio no seletor já no momento do login” ao desenho atual: identidade autenticada não equivale a vínculo/role/simulador autorizado, e não há indicador visual para a espera.

Não foi encontrado redirect externo no fluxo atual: Web usa Google Identity Services em callback na própria página e Android usa o plugin nativo. O console da captura confirma a UID Firebase restaurada, mas não confirma o estado de memberships/simuladores no instante do clique.

## Comparação com o bundle embutido no HF208

A Dist Web 2.3.48 extraída do artefato local do HF208 contém as strings “Atualização sutil em andamento, aguarde.”, “Atualização concluída ✓”, o evento `nvu-live-update-status`, `nvu-live-update-staged`, `pointer-events-none` e a URL compilada do manifesto production-286. Portanto, no Android a mensagem não está ausente por falta de componente no bundle base.

A ausência no Android depende de execução/estado: o manager pode não iniciar no runtime nativo, pode publicar `failed`/`idle` sem fase visual, pode considerar o bundle current/next como já aplicado, ou o evento pode ser perdido na ordem de montagem. O código 2.3.51 corrige a última corrida, mas sem o diagnóstico persistido do HF208 não é possível declarar qual dessas condições ocorreu no aparelho.

## Lacuna comprovada nos gates atuais

`test-profile-session-gate` aprova memberships visíveis durante recovery como estado `ready`, mas não testa o handler real de clique. `test-login-profile-flow` exige que `SelectProfile` enfileire o primeiro toque em `pendingProfileIntentRef` quando `sessionReady=false` e considera isso uma garantia de fluxo. Não existe cenário que pressione o botão nesse estado e verifique feedback visual, tempo de espera ou navegação após a confirmação.

Também não há no gate um cenário que passe um simulador textual/ID ausente da coleção `simulators` por `resolveSimulatorId`/`buildSimulatorSelectorOptions` e exija rejeição. Por isso os gates podem passar enquanto o comportamento observado persiste.

## Reprodução comportamental do simulador órfão

Um reproducer isolado passou `{ simulatorId: "simulador-nao-registrado", simulatorName: "Simulador não registrado" }` contra um catálogo contendo apenas GTO. O resultado atual foi `resolveSimulatorId(...) = "simulador-nao-registrado"`; quando a company contém esse alias, `buildSimulatorSelectorOptions` também gera uma opção exibível com esse valor. A causa do simulador não registrado é, portanto, comprovada no código: valores não presentes no catálogo canônico não são rejeitados quando chegam por company/legacy alias.
