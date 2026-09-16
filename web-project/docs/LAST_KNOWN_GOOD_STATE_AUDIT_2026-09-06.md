# Auditoria Last Known Good State — 2026-09-06

## Resultado

A causa real do retorno para login/loading após períodos longos é a combinação de leitores de cache que aplicam TTL como critério de validade e gates que tratam a ausência causada por esses leitores como ausência de estado. O problema é reproduzível por inspeção estática; não é necessário aumentar TTL nem criar um novo fluxo paralelo.

## Evidências no código atual

| Área | Comportamento atual | Efeito |
| --- | --- | --- |
| `AppContext.readCachedSessionUser` | Retorna `null` quando o cache do usuário ultrapassa 24h | A identidade visual deixa de hidratar no boot frio |
| `AppContext.readCachedMemberships` | Retorna `[]` quando memberships ultrapassam 2h | O seletor perde os vínculos previamente conhecidos |
| `AppContext.hasCachedMembershipSnapshot` | Considera snapshot inexistente após 2h | `membershipsUiReady` deixa de liberar a superfície visual |
| `AppContext.readPersistedOperationalScopeSnapshot` | Remove snapshot após 24h | Dados operacionais não podem ser usados na primeira pintura |
| `AppContext.readOperationalScopeSnapshot` | Retorna `null` após 24h | O escopo é reconstruído a partir do Firestore |
| `CompanyContext.readCachedCompanies` | Retorna catálogo vazio após 30min | Cards de empresa deixam de aparecer no primeiro quadro |
| `CompanyContext.readCachedScopedCompanies` | Retorna vazio após 24h | Empresas vinculadas não são reidratadas imediatamente |
| `activeOperationSnapshot.readActiveOperationSnapshot` | Remove snapshot após 24h | Card da operação ativa desaparece após longa ausência |
| `useTripsRealtime.readPersistedRangeTrips` | Remove cache após 10min | Histórico/ranking volta a depender da leitura inicial |

## Decisão arquitetural

A alteração será incremental e preservará os módulos existentes. Cada leitor passará a distinguir:

- **Inválido/corrompido:** schema incompatível, UID/escopo divergente, timestamp ausente ou inválido, tipo incorreto ou payload estruturalmente inválido. O snapshot não será usado.
- **Stale/expired:** snapshot estruturalmente válido, mas antigo. Será usado somente para hidratação/apresentação e marcado implicitamente como necessitando sincronização.
- **Fresh:** snapshot estruturalmente válido e recente. Pode participar das otimizações de revalidação já existentes.

Os TTLs não serão usados para apagar dados ou impedir a primeira renderização. A autoridade para operações protegidas continua sendo o Firebase Auth e o Firestore. Em particular, memberships antigas poderão pintar o seletor, mas não serão transformadas automaticamente em autorização canônica atual; `membershipsLoaded` e `sessionReady` continuarão dependentes da confirmação corrente, salvo uma confirmação recente já existente na mesma sessão.

O logout continuará removendo caches privados e a chave de UID. O isolamento por UID/empresa/perfil será mantido.

## Escopo local desta correção

Serão ajustados os leitores de sessão, memberships, escopo operacional, empresas, operação ativa e viagens persistidas. Serão adicionados testes unitários/estruturais para snapshot stale, corrupção, escopo divergente, offline e troca de usuário. Não haverá geração de APK, publicação Web/OTA ou alteração de Firebase Rules nesta etapa.
