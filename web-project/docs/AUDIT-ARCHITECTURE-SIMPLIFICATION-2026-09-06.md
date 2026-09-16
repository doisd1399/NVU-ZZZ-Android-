# Auditoria arquitetural de simplificação — NVU Web

## Escopo

Esta auditoria executa a missão do prompt anexado até a entrega de diagnóstico e plano. A regra do prompt determina **não implementar ainda**. Portanto, nesta etapa não foram alterados arquivos de aplicação, não foi gerado APK e não foi feito deploy.

## Índice inicial de complexidade

A contagem estática do diretório `src` encontrou 167 arquivos e aproximadamente 64.395 linhas TypeScript/TSX.

| Métrica | Contagem | Interpretação inicial |
| --- | ---: | --- |
| `useState` | 217 | Muitos estados locais; parte é legítima de tela, mas o caminho crítico mistura estado de UI e sessão. |
| `useEffect` | 172 | Alto número de efeitos; requer separar efeitos de dados secundários de efeitos de navegação/sessão. |
| `useLayoutEffect` | 13 | Alguns são necessários para rota/overlay, mas são sensíveis a races de primeiro paint. |
| `useMemo` | 218 | Complexidade derivada elevada; não é problema isolado, mas dificulta rastrear fontes de verdade. |
| `useCallback` | 53 | Vários callbacks estáveis atravessam contextos e aumentam acoplamento. |
| `onSnapshot` | 42 | Muitos listeners; 10 estão no AppContext, 6 no CompanyContext e outros são de dados secundários. |
| `onAuthStateChanged` | 1 | Uma autoridade Firebase explícita foi localizada; os sintomas não provam dois listeners desse tipo. |
| chamadas `navigate()` | 133 | Navegação distribuída em páginas/layouts; incompatível com uma única decisão de NavigationResolver. |
| elementos `<Navigate>` | 8 | Guards e redirecionamentos declarativos coexistem com efeitos que navegam. |
| chamadas `refreshSession()` | 7 | Refresh ainda participa do caminho de seleção de perfil. |
| `setTimeout` | 67 | Timers de recovery, preload, UI e dados estão distribuídos. |
| `setInterval` | 4 | Inclui lifecycle/OTA; precisa ser separado de dados críticos. |
| `addEventListener` | 31 | Listeners de lifecycle, auth auxiliar, UI e dados misturam responsabilidades. |

## Hotspots de caminho crítico

| Arquivo | Linhas | Responsabilidade acumulada |
| --- | ---: | --- |
| `src/context/AppContext.tsx` | 5.954 | Auth, identidade, memberships, sessão, simuladores, listeners, recovery e ações. |
| `src/App.tsx` | 1.017 | Providers, rotas, guards, resume route, preload, overlays e redirecionamento inicial. |
| `src/context/CompanyContext.tsx` | 925 | Catálogo de empresas, hydration, memberships projetadas e listeners. |
| `src/pages/SelectProfile.tsx` | 906 | Visualização de memberships, hydration de empresas, gate, pending intent e navegação. |
| `src/lib/resolveSimulator.ts` | 374 | Normalização, aliases, grupos e fallback de identidade de simulador. |
| `src/lib/simulatorOptions.ts` | 328 | Reconstrução de opções a partir de catálogo e aliases de companies. |
| `src/lib/otaManager.ts` | 428 | Lifecycle, manifest, validação, download/staging, persistência e eventos OTA. |

## Providers/contextos localizados

`AppContext`, `CompanyContext`, `NotificationsContext`, `PerformanceContext`, `AuthSessionProvider`, `MembershipProvider`, `OperationalDataProvider` e `ProfileSessionProvider` participam do sistema. Os quatro providers em `src/contexts` projetam partes de contextos existentes; isso é candidato forte a unificação, mas não deve ser removido sem mapear consumidores e preservar a única autoridade de autorização.
