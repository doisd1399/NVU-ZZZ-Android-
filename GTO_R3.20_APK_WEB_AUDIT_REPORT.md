# NVU GTO R3.20 — Auditoria APK × Web e Máquina de Estados

## CAUSA RAIZ

A integração anterior não possuía uma fonte de verdade compartilhada para o estado vivo do ciclo GTO. `tripState` era mantido no serviço Android em `SharedPreferences`; a camada Web apenas consultava o bridge nativo quando estava no APK. Não existia um estado de sessão canônico no Firebase que pudesse ser consultado por outra camada/dispositivo e não existia uma validação de transição server-side para impedir uma interpretação divergente.

Havia também um risco de rearmamento: o detector de substituição tratava `TRIP_IN_PROGRESS`, mas não `CONFIRMING_FREIGHT`, deixando uma reabertura da lista durante a confirmação sem o mesmo caminho de recuperação. O projeto ainda possuía scripts de teste legados presos a versões antigas do APK e referências de scripts ausentes no `package.json`.

Por fim, os Web assets já empacotados dentro de `android/app/src/main/assets/public` são anteriores às alterações finais do código Web. Isso não é uma divergência lógica do código-fonte, mas é uma divergência real de artefato: uma release sem `npm run build && npx cap sync android` poderia carregar um WebView antigo.

## DIVERGÊNCIAS APK/WEB CORRIGIDAS

- Criado `functions/src/gtoState.ts` como fonte server-side do ciclo GTO.
- Estados canônicos compartilhados: `IDLE`, `WAITING_FREIGHT`, `CONFIRMING_FREIGHT`, `TRIP_IN_PROGRESS`, `RESULT_DETECTED`, `AWAITING_BONUS_VALIDATION`, `RESULT_CONFIRMED`, `REJECTED_BONUS`, `CANCELLED`.
- Transições são validadas pelo servidor.
- O servidor impede transições impossíveis.
- Nova sessão em `WAITING_FREIGHT` invalida a sessão GTO ativa anterior do mesmo motorista.
- Criado o ponteiro `gto_active_gto_sessions/{driverId}` para consulta Web sem depender de índice composto.
- Criado `src/services/gtoCanonicalState.ts` e `src/hooks/useGtoCanonicalState.ts`.
- `GtoObserverSetup` passou a consultar o estado canônico.
- O Dashboard passou a expor o componente de observação GTO dentro do fluxo da operação.
- O APK publica cada transição aceita ao backend canônico e tenta novamente após falha temporária.
- A reabertura da lista durante `CONFIRMING_FREIGHT` agora pode iniciar o mesmo processo de substituição segura.
- O snapshot antigo continua sendo destruído antes da nova sessão assumir a operação.
- Foram eliminados scripts de teste com referências de versões anteriores incompatíveis com a versão atual.
- `package.json` e `package-lock.json` foram alinhados na versão Web `2.3.1`.
- Android atualizado para `versionCode 36` / `versionName 1.0.36`.

## FLUXOS ANORMAIS AUDITADOS

1. Frete normal → início → conclusão → Receber → envio.
2. Seleção → cancelamento → nova lista → novo frete.
3. Seleção → nenhuma viagem efetivamente iniciada → nova lista → nova tentativa.
4. Saída/reabertura do GTO.
5. Sessão antiga inconsistente → nova seleção.
6. APK publica estado → Web consulta estado canônico.
7. Web possui cliente callable para o mesmo contrato de estado → servidor valida a mesma máquina.
8. Reinício durante estados intermediários.
9. Reaparecimento da lista durante sessão antiga.
10. Sessão abandonada/cancelada não pode gerar viagem posteriormente.

## TESTES

- 36/36 — paridade APK × Web e máquina de estados R3.20.
- 34/34 — integridade end-to-end R3.19.
- 19/19 — seleção/fingerprint R3.17.
- 74/74 — sincronização automática/Firebase.
- 47/47 — fluxo nativo Android.
- 47/47 — fluxo Web/backend.
- 34/34 — cenários runtime R3.8.
- Stress R3.10 — sem duplicação, perda, mutação ou cruzamento de sessão.
- Session races R3.11 — sem escrita cross-session.
- 59/59 — reautorização R3.12.
- R3.16 — ciclo de reabertura do frete aprovado.

## BUILD

O build Android foi tentado com:

`./android/gradlew --no-daemon --offline assembleRelease`

O ambiente não possuía a distribuição Gradle 8.14.3 completa em cache e não conseguiu acessar `services.gradle.org`. Resultado: `UnknownHostException: services.gradle.org`.

Também não foi possível executar `tsc`/Vite porque as dependências Node não estavam disponíveis de forma completa no ambiente após a tentativa de instalação.

Portanto, nenhum APK release é declarado como gerado nesta auditoria.

## ARTIFATO WEB EMBUTIDO

O projeto contém Web assets Capacitor anteriores às últimas alterações. O script `scripts/audit-gto-r3-20-build-sync.mjs` detecta essa situação e falha antes de uma release até que os assets sejam regenerados.

A sequência de release correta é:

`npm install`

`npm run cap:sync:android`

`cd android`

`./gradlew assembleRelease`

## RESULTADO FINAL

A lógica de negócio do ciclo GTO foi consolidada em uma máquina de estados server-side única, com espelhamento no APK/Web e substituição segura de sessões antigas.

A seleção de frete continua fail-closed e o snapshot continua imutável, portanto a nova camada de estado não altera a regra de identidade do frete.

A única pendência técnica deste ambiente é a regeneração dos Web assets e a compilação física do APK release, bloqueadas pela indisponibilidade do Gradle/Vite neste runtime.
