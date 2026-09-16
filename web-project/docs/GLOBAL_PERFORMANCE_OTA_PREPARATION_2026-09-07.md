# Auditoria e otimização global de performance — NVU

## Veredito

**A camada Web foi corrigida e validada localmente para atualização OTA.** Nenhum APK, AAB ou artefato nativo foi gerado. Nenhuma permissão, regra Firestore, configuração Capacitor, AndroidManifest, MainActivity, plugin nativo ou versionCode foi alterado.

Os artefatos locais preparados são Web/Dist `2.3.55` e bundle OTA `production-286-2.3.55`, com `nativeVersionCode: 286`, `runtimeRevision: R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR`, `signatureRequired: true` e `autoUpdateDefault: false`. O bundle não foi publicado.

## Causa raiz comprovada

O atraso crítico não era causado por falta de uma arquitetura de cache. O projeto já possuía cache em memória, `sessionStorage`, hooks compartilhados e reconciliação autoritativa do Firestore. O problema era que alguns perfis bloqueavam o consumo desses dados por gates artificiais.

No `DriverProfileIsolated`, `secondaryReady` começava falso, dependia de `requestAnimationFrame`, desabilitava `useTripHistory` e ainda mantinha `DriverPerformanceCard` atrás de `secondaryReady && companyCatalogLoaded`. Esse caminho foi corrigido anteriormente nesta execução.

No `CompanyTab`, a mesma falha estava presente: `performanceReady` começava falso, era liberado somente no frame seguinte e controlava `useTripHistory(activeCompanyId, { enabled: performanceReady })`. O `CompanyPerformanceCard` também só era montado depois desse gate. Assim, histórico, resumo e desempenho ficavam vazios mesmo quando o cache da empresa já estava disponível.

Além disso, `CompanyPerformanceCard` mantinha `currentRankingLoading` e `classificationTripsLoading` ativos enquanto o catálogo/ranking global sincronizava, mesmo quando havia histórico válido da própria empresa. Isso transformava `REFRESHING_EXISTING_DATA` em uma aparência de `INITIAL_EMPTY_STATE`.

## Correções aplicadas

### Perfil da empresa

`src/pages/admin/fleet/CompanyTab.tsx` agora habilita o histórico assim que `activeCompanyId` existe. O `CompanyPerformanceCard` monta sem aguardar `performanceReady` ou o catálogo global. O catálogo completo continua sendo carregado em `requestIdleCallback` ou timer como tarefa secundária para posição global e informações complementares.

### Desempenho e resumo

`src/components/CompanyPerformanceCard.tsx` agora normaliza o histórico da empresa uma vez e identifica `hasVisibleCompanyHistory`. Se há dados válidos, os indicadores permanecem visíveis durante a reconciliação global. O ranking/classificação só exibe estado de carregamento quando não há dados iniciais disponíveis.

### Perfil do motorista

`src/pages/admin/DriverProfileIsolated.tsx` continua com a correção anterior: o histórico inicia quando `viewedCompanyId` existe e o card não aguarda `secondaryReady` ou `companyCatalogLoaded`.

### Cache e repository

`src/hooks/useTripHistory.ts` e `src/repositories/TripsRepository.ts` foram preservados como fonte única. O cache por empresa, a hidratação persistente, o listener canônico, os aliases legados, o retry e o isolamento por `companyId` continuam ativos. O Firestore permanece responsável pela reconciliação autoritativa em background.

### Ranking e imagens

A auditoria confirmou que o RankingGlobal já publica o snapshot antes do preload de imagens em runtimes mobile ou constrained. O gate de imagens permanece somente no runtime desktop amplo, e não foi alterado. Warmups de imagens e catálogos permanecem secundários e não bloqueiam conteúdo crítico.

### Android/Capacitor

Nenhuma camada nativa foi modificada. Não houve alteração de AndroidManifest, MainActivity, Java/Kotlin, plugins, permissões, configuração nativa ou versionCode/versionName.

## Matriz de validação

| Cenário | Resultado |
| --- | --- |
| Empresa com cache | PASS |
| Empresa sem cache | PASS |
| Motorista com cache | PASS |
| Motorista sem cache | PASS |
| Empresa A → Empresa B | PASS |
| Motorista A → Motorista B | PASS |
| Ranking com cache | PASS estrutural |
| Ranking sem cache | PASS estrutural |
| RH/lista de motoristas | PASS estrutural |
| Perfil aberto pelo RH | PASS estrutural |
| Perfil aberto pelo Ranking | PASS estrutural |
| Firebase lento mantendo cache visível | PASS determinístico |
| Nova viagem registrada | PASS |
| Troca de empresa | PASS |
| Troca de simulador | PASS |
| Reconciliação canônica + aliases | PASS |
| TypeScript/lint | PASS |
| `verify:release` completo | PASS |
| Build Web local | PASS — 2.3.55 |

O `verify:release` foi executado integralmente após o versionamento 2.3.55 e terminou com build Web aprovado. Os testes automatizados não substituem o teste físico no WebView; essa validação física ainda deve ser feita antes de publicar.

## Artefatos OTA preparados

| Artefato | Identidade | SHA-256 |
| --- | --- | --- |
| Dist Web | `2.3.55` | `ad49296423fd2cf32b2ffbdde4787d3b9340d0309a4ea8663632325c84fd096f` |
| Bundle OTA | `production-286-2.3.55` | `61ad96028c9a7af7de1e6e78fe45207174d87130b50babdba83c25314d542b53` |
| Manifesto build | `2.3.55` | `6f50bf8edc8153e4f68c595eefd74b9154d0300b237b77a5410f139caf019eaf` |

O scanner do bundle confirmou ausência de Android, `google-services.json`, `local.properties`, `.env`, keystore e outros segredos.

> Somente a camada web foi alterada e o resultado está preparado para atualização OTA.

## Limite de publicação

A publicação Netlify/OTA não foi executada. O bundle está pronto para validação física no Web/Debug e posterior publicação controlada. Enquanto não houver deploy, a versão pública continua a anterior; a atualização só deve ser aplicada no próximo restart da HF208, sem force reload durante login, uso ou viagem.
