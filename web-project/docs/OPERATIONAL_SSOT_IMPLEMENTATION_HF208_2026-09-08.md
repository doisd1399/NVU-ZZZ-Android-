# Implementação operacional SSOT — HF208

## Identidade

A base utilizada é a HF208 oficial, com package `com.nvu.operacional`, versionCode `286`, versionName `1.0.286` e Web embutido `2.3.58`. O Android foi compilado como Release e Debug com o certificado compatível do release. O canal público `production-286` não foi publicado nem alterado nesta execução.

## Causa raiz corrigida

A conclusão Web distribuía responsabilidades entre `RecordTrip`, sincronização de progresso, `finishJob`, listeners Firestore e notificações. Isso permitia que o card do histórico aguardasse o snapshot remoto, que a contagem de progresso fosse iniciada mais de uma vez e que o job permanecesse em estado intermediário após o último lançamento.

Na camada nativa, a preparação da próxima sessão ainda poderia depender do ACK remoto ou de callbacks de uma sessão anterior. Essa dependência foi separada: o selo local do outbox é a fronteira operacional; o ACK continua confirmando somente a sessão antiga.

## Implementação

Foi criado `operationalCompletionCoordinator.ts` como coordenador único do fluxo Web. Ele aplica idempotência por chave operacional, executa `addTrip`, publica a viagem no store/cache do Histórico após obter o ID durável, persiste o `tripId` no mesmo documento e inicia progresso/conclusão do job em background. `finishJob` continua sendo o proprietário da conclusão, com proteção in-flight, leitura fresca do documento e atualização otimista local.

O APK nativo agora prepara a próxima sessão depois do selo local bem-sucedido quando a política confirma que existe outro frete. A sessão anterior continua no outbox e recebe ACK de forma independente. Callbacks antigos não alteram o estado da sessão atual.

O consumo OTA nativo foi bloqueado de forma explícita. O `OtaManager` permanece disponível como fachada de compatibilidade e para o pipeline Web, mas `start`, `signalReady` e `check` retornam sem executar no Android imutável. A preparação dos assets Capacitor grava `otaEnabled:false`, `otaManifestUrl:""` e `nativeBundleImmutable:true` no `nvu-build.json` embutido no APK. `autoUpdateStrategy` permanece `none`.

## Evidências automatizadas

Foram aprovados TypeScript, lint, `verify:release`, gates de histórico/cache, desempenho global, handoff GTO, primeiro ACEITAR, pressão de transição, coordenador de conclusão, imutabilidade OTA nativa, testes de login, GTO/OCR, ranking, senior, rotas, notificações e persistência offline. Os testes unitários Android não possuem casos executáveis nesta base (`NO-SOURCE`), mas a compilação Java nativa passou.

## Artefatos

| Artefato | Identidade | SHA-256 |
| --- | --- | --- |
| APK Release | `com.nvu.operacional`, 286, `1.0.286`, não debuggable | `444c4558e1a9343a5f91392423b9fc39641b9bfd50ce8d380b3592827ee14bdf` |
| APK Debug | `com.nvu.operacional`, 286, `1.0.286`, debuggable | `cfbbd062b4b5bc2c19f1db0c8769d5c3778408e3111523c78a7502afcc9908f9` |

Ambos contêm Web `2.3.58` e o manifesto nativo imutável. O ZIP não inclui keystore, senha, `google-services.json`, `.env` ou `local.properties`.

## Limites

A validação física ainda é obrigatória. Deve-se testar no Motorola: conclusão do último lançamento, atualização imediata do card, conclusão automática do job, notificação única, handoff para próxima viagem, ACK atrasado, offline, retorno ao GTO, primeiro ACEITAR rápido, duplo toque e ausência de aplicação OTA remota. Nenhum deploy Web/OTA foi executado nesta etapa e nenhum claim de teste físico é feito.
