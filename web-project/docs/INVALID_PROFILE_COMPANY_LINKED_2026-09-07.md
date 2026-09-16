# Correção da opção inválida “Empresa vinculada”

## Causa raiz comprovada

A entrada vinha do `buildProfileIndex` em `src/services/profileIndex.ts`. Para cada membership ativa, o código obtinha o `companyId` e, quando o documento correspondente não estava presente no `CompanyContext`, ainda criava um perfil com `companyName` de fallback:

```text
Empresa vinculada
```

A condição de validade exigia `companyId`, role válida e, quando aplicável, simulador válido, mas **não exigia que o documento da empresa existisse**. O `SelectProfile` agrupava os perfis válidos em `availableCompanies`, por isso o fallback chegava ao dropdown como se fosse uma empresa real. A imagem apresentada é compatível com esse caminho: as empresas reais aparecem normalmente e a última entrada sem identidade empresarial é o fallback.

## Correção aplicada

A validade agora exige o documento real da empresa:

```ts
const valid = Boolean(
  company &&
    companyId &&
    roleDestination(role) &&
    (!simulatorRequired || Boolean(resolvedSimulatorId)),
);
```

Quando existe apenas uma membership ativa sem o documento de empresa, o registro permanece em `invalidProfiles` para diagnóstico, mas não entra em `profiles` e não é renderizado pelo selector. Perfis reais continuam exigindo empresa existente, role válida e simulador válido quando o vínculo exigir simulador.

Não foi removido nenhum perfil real nem alterada a autorização canônica. A correção atua no índice que alimenta a camada visual; `switchRole` e a confirmação server-side permanecem intactos.

## Testes executados

| Verificação | Resultado |
| --- | --- |
| Membership + empresa + GTO | PASS |
| Dois roles reais (Administrador/Motorista) | PASS |
| Membership stale com hidratação local | PASS |
| Simulador desconhecido | PASS — perfil inválido não aparece |
| Empresa ausente + membership ativa | PASS — zero perfis exibíveis; fallback fica somente no diagnóstico |
| Empresa ausente + membership stale | PASS — zero perfis exibíveis |
| Profile session gate | PASS — 9 cenários |
| Last Known Good State funcional | PASS |
| `npm run verify:login` | PASS |
| `npm run lint` | PASS |
| `npm run build` | PASS — Web 2.3.53 |

## Publicação

Esta correção foi validada localmente, mas **não foi publicada** nesta etapa. A Dist/OTA pública `production-286-2.3.53` continua sendo a publicação anterior; não foi reempacotada nem sobrescrita após esta alteração. Nenhum APK foi gerado.

Para colocar esta correção em produção será necessário um novo bundle OTA com identificador novo, por exemplo `production-286-2.3.54`, e uma nova publicação Web/OTA após a validação final do usuário.
