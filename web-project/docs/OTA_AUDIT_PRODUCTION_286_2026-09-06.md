# OTA production-286 — Auditoria técnica

## Escopo e regras

Auditoria somente leitura do OTA público `production-286`. Não foram alterados Android, Capacitor, APK, Web aprovado, OTA, versionCode, canal, Profile Index ou Next View First.

## Estado identificado

- Web aprovado: 2.3.51.
- Runtime revision: `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR`.
- APK/base local: `NVU-R3.34-PC-HF208-web248-local-base-1.0.286-release.apk`.
- Native versionCode: 286.
- Native versionName: 1.0.286.
- Canal nativo derivado: `production-286`.
- APK embute `capacitor.config.json` com `autoUpdateStrategy: none`, `autoBlockRolledBackBundles: true`, `readyTimeout: 10000`, canal `production-286` e chave pública RSA configurada.

## Manifesto e artefato local

Manifesto auditado: `manifest-production-286-2.3.51.json`.

- artifactType: zip.
- bundleId: `production-286-2.3.51`.
- webVersion: 2.3.51.
- nativeVersionCode: 286.
- nativeChannel: production-286.
- runtimeRevision compatível.
- downloadUrl HTTPS e same-origin.
- checksum SHA-256 presente.
- signatureRequired: true.

Bundle local: `nvu-live-update-2.3.51-production-286.zip`.

- tamanho: 16.154.619 bytes.
- checksum calculado: `de3dd033227b02ceef2ee6051410e58859b076b68bef5d552317b2ddcf27763e`.
- checksum do manifesto: igual ao calculado.
- teste `unzip -tq`: aprovado.
- entradas Android, Capacitor, google-services, keystore, PEM ou P12: não encontradas.
- assinatura RSA com a chave pública local configurada: aprovada.

## Artefato remoto

URL remota: `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/nvu-live-update-2.3.51.zip`.

A resposta pública foi HTTP 200, Content-Type `application/zip`, Content-Length 16.154.619 e o ZIP remoto foi baixado, testado com `unzip -tq` e teve o mesmo SHA-256 do artefato local. A comparação local/remota foi aprovada.

## Observação de build

O manifesto OTA usa `buildId: local-2026-09-06T11:18:33.682Z`, enquanto o deploy Web corrigido posterior possui buildId diferente. A igualdade de buildId não é exigida pelo runtime; a compatibilidade relevante continua sendo Web version/runtime revision/canal/versionCode e a validade do bundle assinado. Essa diferença será classificada explicitamente no relatório final.

## Compatibilidade APK e diferença de conteúdo Web

A configuração extraída do APK HF208 confirma `defaultChannel: production-286`, `autoUpdateStrategy: none`, `autoBlockRolledBackBundles: true`, `readyTimeout: 10000` e chave pública RSA presente. A impressão digital SHA-256 da chave pública embutida no APK coincide com a chave pública usada para validar a assinatura do bundle.

O APK embute Web base 2.3.48 e `nvu-build.json` próprio; isso é esperado para o fallback local. O OTA production-286 é Web 2.3.51 com runtimeRevision igual à raiz pública.

A raiz pública corrente após o deploy Web corrigido possui buildId `local-2026-09-06T13:21:05.346Z`. O manifesto e o ZIP OTA production-286 possuem buildId `local-2026-09-06T11:18:33.682Z`. O bundle OTA contém o asset `assets/index-DY2QQ8D_.js` e não contém o marcador `signInWithPopup`; a Dist Web corrente possui `index-BshAIVr_.js` e contém `signInWithPopup`/tratamento `auth/popup-blocked`. Portanto, embora versão Web (2.3.51) e runtimeRevision coincidam, o OTA representa uma build funcional anterior à correção Google Auth Web. O prompt permite buildId diferente quando legitimamente gerado, mas exige determinar a coerência funcional; essa diferença deve ser classificada como WARNING/FAIL na decisão final, não ignorada.

## Rollback e reload

A configuração nativa do APK ativa `autoBlockRolledBackBundles: true`, fixa `readyTimeout` em 10 segundos e usa `autoUpdateStrategy: none`. O manager chama `LiveUpdate.ready()` de forma não bloqueante, baixa com checksum/signature, executa `setNextBundle` e não chama reload. Os gates OTA confirmam staging no próximo restart, continuidade da sessão, serialização e ausência de reload. Rollback destrutivo não foi executado, conforme a regra da auditoria; a proteção configurada e o contrato estrutural foram verificados.

## Preservação pública

Os manifestos públicos `production-278`, `280`, `281`, `282`, `283`, `284`, `285` e `286` retornaram HTTP 200 após o deploy Web corrigido. O OTA production-286 remoto retornou HTTP 200, 16.154.619 bytes, checksum igual ao local e ZIP íntegro.

## Classificação final

| Categoria | Resultado | Evidência |
| --- | --- | --- |
| Manifest | PASS | Manifesto local e público byte-identical, HTTP 200, JSON válido e campos estruturais presentes. |
| ZIP | PASS | Bundle local/remoto com 16.154.619 bytes, `unzip -tq` aprovado e estrutura Web esperada. |
| Checksum | PASS | SHA-256 local, remoto e manifesto: `de3dd033227b02ceef2ee6051410e58859b076b68bef5d552317b2ddcf27763e`. |
| Assinatura | PASS | RSA-SHA256 validada com a chave pública; fingerprint da chave do APK coincide com a chave usada na validação. |
| HTTPS/origem | PASS | Download HTTPS e same-origin com o manifesto. |
| Canal/versionCode/runtime | PASS | `production-286`, nativeVersionCode 286 e runtimeRevision igual ao APK/configuração. |
| nvu-build coerente | WARNING | Versão/runtime/OTA são coerentes, mas o buildId do OTA é `local-2026-09-06T11:18:33.682Z`; a raiz Web corrigida atual é `local-2026-09-06T13:21:05.346Z`. |
| Conteúdo funcional Web/OTA | FAIL para equivalência com o Web aprovado atual | O OTA não contém `signInWithPopup`; a raiz Web pública atual contém o fallback Google Auth. Ambos declaram 2.3.51, mas não são o mesmo conteúdo funcional. |
| Download/staging | PASS estrutural | `downloadBundle` recebe artifactType, bundleId, URL, checksum e signature; `setNextBundle` ocorre após verificação. |
| Próximo restart | PASS estrutural | `autoUpdateStrategy: none`, `setNextBundle` sem reload e `readyTimeout: 10000`. |
| Reload durante sessão | PASS | O manager não chama reload; gate `A staged update does not force reload` aprovado. |
| Rollback protection | PASS estrutural / NOT VALIDATED em dispositivo | APK tem `autoBlockRolledBackBundles: true`; rollback destrutivo não foi executado em produção por regra do prompt. |
| Pipeline | PASS | `verify:ota-ready`, `test:ota-manager-contract` 17/17, `test:live-update-status` 20/20 e `verify-netlify-ota` passaram. |
| Histórico público | PASS | Manifestos 278, 280, 281, 282, 283, 284, 285 e 286 retornaram HTTP 200. |

## Decisão

**REPROVADO — não avançar para APK nem substituir o OTA production-286.**

A reprovação não é causada por checksum, assinatura, canal, versionCode, runtime, origem, ZIP ou proteção de rollback; esses itens passaram. A causa técnica exata é a divergência funcional entre a raiz Web 2.3.51 atualmente aprovada/publicada e o bundle OTA production-286: o bundle OTA foi produzido antes da correção do fallback `signInWithPopup` e continua com o buildId/asset anterior.

Nenhuma correção foi feita nesta auditoria. Para alinhar o APK ao Web aprovado, será necessário um novo processo de release OTA: gerar novo bundle, assinar, recalcular manifesto, verificar novamente e publicar no mesmo canal `production-286`. Esse processo não foi iniciado, conforme a regra do prompt. Nenhum APK foi gerado e nenhum arquivo Android, Web aprovado ou OTA existente foi substituído.
