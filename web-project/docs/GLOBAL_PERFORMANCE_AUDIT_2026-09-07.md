
## Gates restantes auditados

`src/pages/driver/Profile.tsx` usa `requestIdleCallback`/timer para carregar o catálogo global após a identidade e viagens do motorista; esse trabalho é explicitamente secundário. A ocorrência de `requestAnimationFrame` no final do arquivo apenas marca `T11_PROFILE_FIRST_PAINT` depois que o perfil já está renderizado; não bloqueia dados nem deve ser removida.

`src/pages/RankingGlobal.tsx` mantém snapshot em memória com TTL de dois minutos por chave de período/escopo. `gateInitialPublishOnImages` é ativado somente em runtime desktop não-constrained; em mobile e runtime constrained o snapshot é publicado antes do preload de imagens. A lógica de imagens, portanto, não constitui o atraso crítico no APK/WebView e não será alterada sem evidência adicional.

O CompanyTab, por outro lado, tinha gate comprovado e foi alterado localmente: o histórico agora habilita assim que `activeCompanyId` existe e o `CompanyPerformanceCard` monta sem aguardar `performanceReady`. O catálogo global continua em idle/timeout para dados de posição e ranking global.
