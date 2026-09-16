# Auditoria — remoção de funcionário da frota e atualização imediata da UI

**Data:** 16 de setembro de 2026  
**Escopo:** painel Administrativo, aba Funcionários, remoção de motorista da frota.

## Sintoma

Após remover um funcionário, o documento era excluído ou atualizado no Firestore, mas o card permanecia visível até o aplicativo ser reiniciado.

## Causa raiz

A função `removeDriverFromFleet` executava uma operação atômica com `writeBatch` e aguardava `removalBatch.commit()`. Porém, depois do commit, não havia atualização imediata das projeções locais usadas pelo `DriversTab`.

A lista de cards é derivada de `allCompanyMembers` e `users`. O `DriversTab` calcula `getDriverRoles(driver)` a partir dos membros ativos e, em seguida, filtra os usuários. Essas estruturas dependiam do próximo `onSnapshot` de `companyMembers` e `users`. Mesmo com a gravação confirmada, o listener podia publicar o novo snapshot depois de uma latência de rede, cache ou ciclo de atualização do WebView. Por isso o dado já estava correto no backend, mas a interface permanecia visualmente obsoleta.

## Correção aplicada

Após `await removalBatch.commit()`, a função agora remove imediatamente o funcionário de três projeções locais:

| Projeção | Atualização |
|---|---|
| `allCompanyMembers` | Remove o membro cujo `companyId` e `userId` correspondem à remoção. |
| `users` | Remove o usuário removido da lista operacional corrente. |
| `fetchedMissingUsers` | Remove o mesmo usuário da projeção de fallback. |

A atualização ocorre somente depois do commit confirmado. Portanto, uma falha de permissão ou rede não altera a UI como se a remoção tivesse sido concluída. Os snapshots subsequentes continuam sendo a reconciliação autoritativa com o Firestore.

## Validação

| Verificação | Resultado |
|---|---:|
| Regressão de remoção imediata | **5/5 PASS** |
| TypeScript (`npm run lint`) | **PASS** |
| Build Web (`npm run build`) | **PASS** |
| Verificação de release Web | **PASS** |

O ajuste está no `src/context/AppContext.tsx`, com teste em `scripts/test-fleet-removal-immediate-ui.mjs` e script npm `test:fleet-removal-immediate-ui`.

## Impacto

A alteração não modifica as regras Firestore nem amplia permissões. Ela apenas elimina, após sucesso confirmado, o atraso visual das projeções locais. A atualização também funciona para remoções iniciadas pelo painel Senior, pois ambas utilizam `removeDriverFromFleet`.
