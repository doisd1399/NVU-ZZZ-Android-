# Auditoria de sessão offline — HF208

## Escopo

A auditoria foi feita na base oficial HF208 (`com.nvu.operacional`, Android versionCode 286) sem alterar regras Firestore, lógica GTO/OCR ou camada nativa.

## Cadeia atual

| Etapa | Fonte | Cache | Offline atual | Risco comprovado |
| --- | --- | --- | --- | --- |
| Firebase Auth | Firebase Web `browserLocalPersistence`; fallback Capacitor Google | IndexedDB/Auth nativo | Parcialmente | `authStateReady()` pode depender da abertura do IndexedDB; o observer já posterga `null`, mas não existe uma camada única de sessão offline |
| Usuário | documento `users/{uid}` e fallback UID Google | `nvu.session.v5.user.{uid}` | Sim, se UID conhecido | Cache é UID-scoped, estruturalmente validado e não tem TTL destrutivo |
| Memberships | `companyMembers` por `userId` | `nvu.session.v5.memberships.{uid}` | Sim para UI | Cache stale é preservado, mas autorização continua dependente de confirmação server-side |
| Empresa | `frotas/{companyId}` e catálogos | `nvu.session.v5.scoped-companies.{uid}` + `nvu.public.companies.v5` | Parcialmente | A empresa só é reconstruída se o catálogo escopado/publico já contiver o documento; não há snapshot atômico unindo membership + empresa + contexto |
| Simulador | coleção `simulators` | `nvu.public.simulators.v1` | Parcialmente | Catálogo é reidratado no estado inicial, mas o perfil depende do `simulatorId` presente na empresa e do ProfileIndex |
| ProfileIndex | `buildProfileIndex` | Não diretamente | Sim quando user + memberships + companies estão disponíveis | `company` real é obrigatório; `USER + MEMBERSHIP + SIMULATOR` com empresa ausente vira `PROFILE_CONTEXT_UNRESOLVED` |
| Selector | `SelectProfile` | Indireto pelo ProfileIndex | Sim se index estiver `ready` | `resolving/error/empty` mantém telas de diagnóstico/loading |
| ProtectedRoute | `sessionUiReady` + perfil selecionado + membership/empresa | Não | Não autoriza offline | Correto para segurança: UI pode restaurar, ações protegidas devem continuar server-authorized |

## Causas comprovadas

A base já evita parte do problema: `onAuthStateChanged(null)` é adiado até persistência Web e restauração nativa terminarem; memberships stale não são apagadas por respostas locais/temporárias; CompanyContext preserva catálogo quando uma consulta falha.

A lacuna arquitetural real é a ausência de um **snapshot único, atômico e UID-scoped** contendo usuário, memberships, empresas, simuladores e contexto ativo. Hoje cada camada restaura sua própria chave. Um cold boot offline pode, portanto, restaurar o usuário e memberships, mas iniciar empresas/simuladores em estados diferentes; o ProfileIndex então não consegue formar um perfil válido.

O Firestore é criado apenas por `getFirestore(app)`, sem `persistentLocalCache()` na instância única. Assim, os caches NVU funcionam como aceleração, mas listeners/query snapshots do Firestore não possuem a persistência offline moderna da SDK.

## Correção planejada

A correção será cirúrgica: adicionar um contrato de snapshot atômico na arquitetura existente, ler esse snapshot no boot e alimentar os estados já existentes de usuário, memberships, empresas, simuladores e contexto. O snapshot só será gravado quando os dados estiverem estruturalmente consistentes e UID-coerentes. Cache stale poderá hidratar UI, mas não autorizará operações protegidas.

Também será habilitado `persistentLocalCache()` na instância Firestore já existente, com fallback seguro para `getFirestore(app)` quando IndexedDB não estiver disponível. Nenhuma segunda instância será criada.

Logout explícito/troca confirmada continuam sendo os únicos caminhos de limpeza completa. Falha de rede, timeout, erro Firestore, erro de plugin ou Auth ainda em restauração não poderão limpar o snapshot.
