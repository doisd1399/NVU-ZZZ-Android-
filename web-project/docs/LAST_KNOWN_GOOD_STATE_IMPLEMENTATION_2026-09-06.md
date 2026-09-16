# Implementação Last Known Good State — 2026-09-06

## Status

A correção arquitetural foi implementada e validada localmente no projeto Web/OTA do NVU. A aplicação agora preserva snapshots estruturalmente válidos mesmo quando antigos e permite que a interface seja hidratada a partir do último estado conhecido enquanto o Firestore reconcilia em background.

> **Regra aplicada:** `stale/expired` não significa `invalid/corrupted`. A idade orienta a necessidade de sincronização; não destrói a capacidade de primeira pintura.

## Causa raiz confirmada

Os leitores de sessão, memberships, empresas, escopo operacional, operação ativa e viagens persistidas usavam TTL como critério de validade. Quando o prazo expirava, o código retornava `null`/`[]` ou removia o snapshot. Isso fazia o boot parecer um primeiro acesso, reativava gates de loading e fazia a interface depender de novas leituras do Firestore.

## Alterações realizadas

| Arquivo | Alteração |
| --- | --- |
| `src/context/AppContext.tsx` | Usuário, memberships e escopo operacional stale continuam disponíveis para hidratação quando UID, schema e payload são válidos. A confirmação recente de memberships mantém TTL separado para o warm authorization. Logout e isolamento por UID foram preservados. |
| `src/context/CompanyContext.tsx` | Catálogos público e escopado por UID não são mais esvaziados apenas por idade; dados inválidos continuam rejeitados e o Firestore continua atualizando em background. |
| `src/lib/activeOperationSnapshot.ts` | Operação ativa antiga permanece disponível para primeira pintura; schema, UID, empresa, timestamp e job ainda são validados. |
| `src/hooks/useTripsRealtime.ts` | Histórico persistido antigo permanece disponível enquanto o listener realtime reconcilia; snapshots malformados continuam sendo descartados. |
| `scripts/test-last-known-good-state.mjs` | Novo gate estrutural para stale versus invalid, isolamento, autorização e limpeza de logout. |
| `scripts/test-persistent-warm-boot.mjs` | Expectativa atualizada para a política sem TTL destrutivo. |
| `scripts/test-profile-operation-instant-surface.mjs` | Expectativa atualizada para snapshots estruturalmente válidos sem TTL destrutivo. |
| `scripts/test-native-session-selector-startup.mjs` | Expectativas alinhadas ao Router/SelectProfile executável atual, removendo verificações de comentários e símbolos de uma implementação anterior. |
| `package.json` | Novo script `test:last-known-good-state` incluído no `verify:login`. |
| `docs/LAST_KNOWN_GOOD_STATE_AUDIT_2026-09-06.md` | Auditoria factual e decisão arquitetural registradas. |

## Segurança preservada

O snapshot local continua sendo somente uma projeção de apresentação. `sessionReady` permanece derivado de identidade Firebase coerente e `membershipsLoaded`; o snapshot antigo não concede sozinho acesso a Admin, Driver ou ações sensíveis. A autorização canônica continua dependendo do Firebase/Firestore. O logout continua removendo projeções privadas e `sessionStorage`, e os snapshots continuam escopados por UID, empresa e perfil.

## Validações executadas

| Validação | Resultado |
| --- | --- |
| Novo gate Last Known Good State | Aprovado |
| Warm boot persistente | Aprovado |
| TypeScript (`npm run lint`) | Aprovado |
| Fluxo formal de login (`npm run verify:login`) | Aprovado |
| Startup/selector nativo | Aprovado |
| Auth persistence order e estabilidade | Aprovado |
| Session resume route | Aprovado |
| Histórico e operação instantânea | Aprovado |
| Build Web local (`npm run build`) | Aprovado |
| Gate formal completo (`npm run verify:release`) | Aprovado |

O build confirmou Web `2.3.52`, runtime local e manifesto presente no `dist`. O aviso de chunk grande do Vite permaneceu apenas como warning de otimização; não impediu o build.

## Limites desta execução

Não foi gerado APK. Não foi executado `assembleRelease`. Não houve deploy Netlify, publicação OTA, alteração de Firebase/OAuth, alteração de Firestore Rules ou mudança de lógica de negócio. A validação física no Motorola Edge 50 ainda é necessária para medir o comportamento real após longa ausência; o código e os gates locais estão aprovados, mas isso não substitui o teste no dispositivo.

## Próximo passo controlado

Antes de publicar, instalar a Dist local em ambiente de teste ou executar a aplicação Web/Debug existente, simular sessão persistida com snapshot antigo, confirmar que o seletor aparece sem retornar à tela neutra e observar a reconciliação posterior. Somente após essa validação física deve ser preparado um bundle OTA; nenhum bundle público foi alterado por esta execução.
