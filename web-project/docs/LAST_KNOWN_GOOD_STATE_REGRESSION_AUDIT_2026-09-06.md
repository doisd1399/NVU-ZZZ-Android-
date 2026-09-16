# Auditoria da regressão funcional — Last Known Good State

## Causa comprovada

A implementação anterior preservou snapshots stale, mas não completou a hidratação funcional do selector e do perfil.

| Etapa | Comportamento observado no código | Consequência |
| --- | --- | --- |
| `AppContext` | Memberships stale são carregadas em `memberships`, mas `membershipsLoaded` permanece `false` até confirmação da geração atual | O estado existe em memória, porém não é consumido pelo índice de perfis |
| `buildProfileIndex` | Retorna `resolving` e `profiles: []` sempre que `membershipsLoaded` é falso | O selector mostra `ProfileSelectionTransition` em vez de perfis restaurados |
| `SelectProfile` | Depende de `profileIndex.status === "ready"` para montar perfis e ações | O usuário não vê Administrador/Motorista durante a reconciliação |
| `simulators` | Começa como `[]` e só é preenchido por listener Firestore; não há snapshot local do catálogo | Mesmo com empresa stale, `resolveRegisteredSimulatorId` não encontra o simulador e pode classificar o perfil como inválido |
| `activeProfileContext` | Só é criado quando `profileIndex.status === "ready"` | Rota retomada não recebe contexto completo de empresa/simulador |
| `switchRole` | Valida membership em memória e atualiza perfil; sem distinção explícita entre snapshot visual e confirmação atual | A correção precisa liberar hidratação visual sem transformar snapshot stale em autorização canônica |

## Estado funcional que deve ser restaurado

Para uma conta com dois perfis, Administrador e Motorista, com Administrador selecionado antes do fechamento, o boot local precisa reconstruir: UID/usuário, memberships, catálogo/identidade da empresa, simulador registrado, roles derivadas, activeRole, activeCompanyId, `ProfileIndex`, contexto ativo, dados operacionais e rota de retomada.

## Regra de segurança

A hidratação stale pode construir a superfície visual e os dados de primeira pintura, mas não marca `membershipsLoaded` como confirmação atual. `sessionReady` continua exigindo identidade Firebase coerente e memberships confirmadas. A fase de reconciliação deve substituir o snapshot quando houver mudança real, sem limpar a superfície enquanto a consulta está pendente.

## Correção planejada

1. Permitir que `buildProfileIndex` consuma um snapshot de memberships para apresentação, mantendo um campo de origem/autoridade separado.
2. Persistir e reidratar um catálogo mínimo de simuladores associado ao snapshot de empresas, ou resolver o simulador registrado diretamente da empresa stale sem aceitar identificadores não semânticos.
3. Passar a informação de hidratação visual do AppContext ao ProfileIndex sem alterar `sessionReady`.
4. Criar teste funcional com snapshot >24h e Firestore inicialmente indisponível, cobrindo dois perfis, empresa, simulador, selected profile, rota e dados operacionais.
5. Confirmar que a reconciliação posterior não apaga a primeira pintura e que logout/troca de usuário continuam isolados.
