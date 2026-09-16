# VALIDAÇÃO WEB — NEXT VIEW FIRST

**Data:** 2026-09-06  
**Projeto:** NVU Web 2.3.51  
**Escopo:** Auth → Profile Index → Active Profile Context → seleção → navegação inicial.  
**Decisão:** **REPROVADO nesta rodada de validação pública**, com correção local preparada e não publicada.

## Ambiente

A aplicação foi aberta em dois ambientes. O servidor local foi executado com Vite em `localhost:3000`, atrás de um proxy temporário apenas para permitir a inspeção do navegador. O teste público foi executado em `https://stirring-pavlova-ca6808.netlify.app/`.

O domínio público respondeu com a tela NVU e com a rota `/login`. O `nvu-build.json` público informou Web `2.3.51`, runtime `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR`, `otaEnabled: true` e manifesto `production-286`. O manifesto público informou `bundleId: production-286-2.3.51`, `nativeVersionCode: 286` e checksum `de3dd033227b02ceef2ee6051410e58859b076b68bef5d552317b2ddcf27763e`.

## Cenário 1 — Perfil único

**Resultado: não executado.** O fluxo não passou da autenticação Google pública. Não houve sessão autenticada disponível para validar a cardinalidade do Profile Index ou a autoabertura de um único perfil.

## Cenário 2 — Múltiplos perfis

**Resultado: não executado.** O fluxo não passou da autenticação Google pública.

## Seleção

**Resultado: não validado em sessão real.** Os gates locais confirmam que o SelectProfile usa o Profile Index, não possui `pendingProfileIntentRef`/`profileRefreshInFlightRef` e não inicia refresh/hydration no clique. Entretanto, o teste funcional público não chegou ao seletor.

## Falha de autenticação observada

Na rota pública `/login`, ao clicar em `Entrar com Google`, a interface exibiu:

> “O seletor Google não foi exibido. Verifique se o domínio está autorizado e tente novamente.”

A investigação da página confirmou que `window.google.accounts.id` existe e que `https://accounts.google.com/gsi/client` foi carregado. Uma chamada diagnóstica controlada a `google.accounts.id.prompt()` retornou:

```json
{
  "displayed": false,
  "notDisplayed": false,
  "skipped": true,
  "skippedReason": "unknown_reason",
  "origin": "https://stirring-pavlova-ca6808.netlify.app"
}
```

O bundle público usa apenas `google.accounts.id.prompt()` para o fluxo Web. Quando o prompt é ignorado, o código encerra a tentativa com `GOOGLE_IDENTITY_NOT_DISPLAYED`; o componente de Login apenas mostra a mensagem. Não existia fallback Web para um seletor OAuth determinístico.

A **causa funcional comprovada** é a dependência exclusiva do prompt One Tap/GIS. O motivo externo específico do `unknown_reason` — por exemplo, origem OAuth não autorizada, estado da sessão Google ou política do navegador — não foi isolado a 100% apenas pelo retorno público. A mensagem exibida pela UI não é prova suficiente de que o domínio esteja necessariamente ausente da configuração autorizada.

## Correção mínima aplicada localmente

Foi aplicado somente o ponto diretamente responsável, sem tocar em Android, Capacitor, OTA, Firebase backend, regras de negócio, Profile Index ou layout:

| Alteração | Resultado |
| --- | --- |
| `googleAuthService.ts` | Adicionado `signInWithPopup` como fallback Web quando GIS retorna `GOOGLE_IDENTITY_NOT_DISPLAYED`. |
| Estado de autenticação | O fallback inicia uma nova tentativa explícita, mantendo a máquina de estados de tentativa como fonte única. |
| Android | Continua usando exclusivamente `signInWithNative`; o popup não é usado no runtime nativo. |
| Redirect | Nenhum `signInWithRedirect` ou `getRedirectResult` foi introduzido. |
| Testes | Gate Google passou a exigir GIS primeiro + fallback popup + ausência de redirect. |

## Teste de background

**Não validado em sessão pública autenticada.** Estruturalmente, a seleção continua atualizando o Active Profile Context de forma síncrona e deixando persistência/`updateDoc`/hidratação secundária fora da pré-condição de navegação.

## Teste de conexão lenta

**Não executado em sessão autenticada.** O código local mantém o clique do perfil sem `refreshSession`, `loadCompanyById`, recovery ou listener como pré-condição.

## Teste de duplo clique

**Não executado visualmente em sessão autenticada.** Os gates locais continuam aprovando a autoridade única e a ausência do mecanismo antigo de pending intent.

## Teste de refresh/reabertura

**Não executado em sessão autenticada nesta rodada.** O gate local de warm boot passou e confirma que a decisão inicial usa o Profile Index, enquanto a memória de rota continua UID-scoped e não decide a entrada antes do índice.

## Perfil inválido e G. Truck

A validação estrutural local passou. O Profile Index rejeita simulador desconhecido, membership inativa, role inválida e company inválida. O perfil isolado não usa mais `G. Truck` como identidade operacional; quando não há correspondência canônica, exibe `Simulador não vinculado`.

Esses cenários não foram exercitados numa sessão pública porque a autenticação falhou antes do seletor.

## Console e Network

O GIS carregou e as conexões Firestore foram observadas no domínio público. Não foi observado erro de carregamento ausente do script GIS. O retorno capturado pelo próprio GIS foi `skippedReason: unknown_reason`; a aplicação converteu esse retorno em sua mensagem genérica.

## Gates locais após a correção

| Verificação | Resultado |
| --- | --- |
| `npm run lint` | Aprovado |
| `npm run test:google-auth-state` | Aprovado: 3 cenários + contrato fallback popup |
| `npm run test:login-identity-reconciliation` | Aprovado: 6 fixtures + 20 checks |
| `npm run test:auth-session-stability` | Aprovado |
| `npm run test:login-profile-flow` | Aprovado |
| `npm run test:login-profile-recovery` | Aprovado |
| `npm run verify:login` | Aprovado dentro do gate completo |
| `npm run verify:release` | Aprovado localmente após atualizar o gate compatível |
| `npm run build` | Aprovado; Web 2.3.51 compilado |

## Resultado final

**REPROVADO para validação funcional real no Web público nesta rodada.**

A arquitetura Next View First local está coberta e o ponto direto da falha de login foi corrigido localmente com fallback popup. Porém, a correção ainda não foi publicada e, portanto, o domínio público continua executando o bundle anterior sem esse fallback. Além disso, a sessão autenticada real não foi concluída no navegador; por isso não é possível declarar aprovação dos cenários de perfil, seleção, persistência, rede lenta ou duplo clique.

Não foi gerado APK. Não foi publicado OTA. Não foi feito deploy Web nesta etapa. Nenhuma alteração Android foi realizada.

O próximo passo seguro é validar a origem OAuth/Firebase para `stirring-pavlova-ca6808.netlify.app` e, somente com autorização explícita para publicar a correção Web, fazer um novo deploy Web e repetir a autenticação pública. Se o popup também retornar `auth/unauthorized-domain`, a causa externa será confirmada como configuração de domínio autorizado; se abrir, o fallback terá resolvido a falha de prompt.

## Referências

[1]: https://stirring-pavlova-ca6808.netlify.app/ — domínio público NVU validado.

[2]: https://stirring-pavlova-ca6808.netlify.app/nvu-build.json — manifesto público do build Web 2.3.51.

[3]: https://stirring-pavlova-ca6808.netlify.app/ota/production-286/manifest.json — manifesto público OTA production-286.

[4]: https://accounts.google.com/gsi/client — biblioteca pública Google Identity Services carregada pelo Web.
