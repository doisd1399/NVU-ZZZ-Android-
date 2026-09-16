# Correção da regressão funcional — Last Known Good State

## Veredito desta etapa

> **REPROVED — não aprovado para publicação ainda.**

O cenário funcional automatizado foi corrigido e passou, mas o comportamento no WebView/Android real ainda não foi repetido nesta execução. Portanto, não é factual declarar `APPROVED` conforme o critério do anexo.

## Causa raiz reproduzida

A implementação anterior preservava snapshots stale no `AppContext`, porém ainda havia duas perdas de estado durante o boot:

1. `buildProfileIndex` exigia `membershipsLoaded === true`. Assim, memberships stale existentes em memória não eram convertidas em perfis; o índice retornava `resolving` com `profiles: []`, e `SelectProfile` renderizava uma tela neutra em vez do Administrador/Motorista.
2. O catálogo de simuladores começava como `[]` e só era preenchido pelo listener Firestore. Como `resolveRegisteredSimulatorId` exigia o catálogo global, a empresa stale com `simulatorId` conhecido podia ser classificada como perfil inválido antes da resposta remota.
3. Durante `identityPendingAtStart`, uma resposta vazia do listener podia executar `setMemberships([])`, destruindo a hidratação local enquanto a reconciliação ainda estava pendente. Essa é uma race condition de fase B substituindo fase A.

## Correção aplicada

| Área | Correção |
| --- | --- |
| `src/services/profileIndex.ts` | Novo sinal explícito `membershipsHydrated` permite construir o índice visual com memberships stale UID-scoped, mantendo `membershipsLoaded` separado. O simulador persistido na empresa é usado apenas como fallback de primeira pintura quando o catálogo global ainda está vazio. |
| `src/context/AppContext.tsx` | Catálogo normalizado de simuladores é persistido e reidratado como projeção pública visual. O listener atualiza o cache quando confirma dados. Em erro, o último catálogo permanece visível. O ProfileIndex recebe `membershipsUiReady` como `membershipsHydrated`. |
| `src/context/AppContext.tsx` | A resposta vazia durante `identityPendingAtStart` preserva `membershipsRef`/`cachedMemberships`; só grava vazio quando não há snapshot local. |
| `scripts/test-profile-index.mjs` | Adicionado cenário com dois roles, memberships stale, empresa e simulador sem catálogo global. |
| `scripts/test-last-known-good-state-functional.mjs` | Novo cenário funcional local: snapshot com mais de 24h, Firestore inicialmente indisponível, Administrador selecionado, Motorista disponível, empresa/simulador presentes e reconciliação posterior sem alteração visual. Também protege a race de memberships vazias. |
| `package.json` | O teste funcional passou a integrar `verify:login`. |

## Evidência automatizada

Os seguintes gates passaram após a correção:

| Gate | Resultado |
| --- | --- |
| `npm run test:profile-index` | PASS — 6 cenários |
| `npm run test:last-known-good-state-functional` | PASS — hidratação, selector, Administrador selecionado, reconciliação e race vazia |
| `npm run test:last-known-good-state` | PASS |
| `npm run lint` | PASS |
| `npm run verify:login` | PASS, incluindo o novo teste funcional |
| `npm run build` | PASS — Web 2.3.52 |
| `npm run verify:release` | PASS local |

## O que ainda não foi comprovado

Ainda não foi executado o cenário físico no Motorola Edge 50/WebView com dados reais: fechar após selecionar Administrador, aguardar mais de 24 horas ou simular timestamp stale, reabrir, confirmar primeira pintura funcional, trocar para Motorista, fechar, reabrir e verificar a persistência da escolha. Os testes atuais validam a cadeia de dados e os gates, mas não substituem esse teste de renderização real.

## Restrições respeitadas

Nenhum APK foi gerado. Nenhuma variante Debug ou Release foi gerada. Android nativo não foi alterado. Nenhum bundle OTA foi preparado ou publicado. Netlify, Firebase/OAuth, Firestore Rules e a base HF208 permaneceram sem publicação nesta etapa.

## Próximo passo obrigatório

Executar o teste funcional no Web/Debug já disponível, usando o WebView/Edge DevTools para observar a primeira pintura, o selector e a reconciliação. Somente se esse cenário real passar sem tela neutra, perfil vazio ou perda de seleção a implementação poderá mudar de `REPROVED` para candidata à publicação Web/OTA.
