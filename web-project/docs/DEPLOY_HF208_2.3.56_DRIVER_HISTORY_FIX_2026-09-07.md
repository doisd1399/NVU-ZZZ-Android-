# Deploy HF208 — correção do histórico imediato 2.3.56

## Resultado

A correção do carregamento imediato de `Viagens da operação` foi publicada na Dist Web e na OTA do canal nativo `production-286`.

| Campo | Valor |
| --- | --- |
| Web | `2.3.56` |
| Bundle OTA | `production-286-2.3.56` |
| Android versionCode | `286` |
| Runtime revision | `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR` |
| Deploy ID | `6a9eb39b5dae76b14eda6d8c` |
| URL pública | `https://stirring-pavlova-ca6808.netlify.app` |
| URL imutável | `https://6a9eb39b5dae76b14eda6d8c--stirring-pavlova-ca6808.netlify.app` |
| Manifesto | `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/manifest.json` |
| Bundle | `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/nvu-live-update-2.3.56.zip` |
| SHA-256 remoto | `ced49ca630d86a9393b5d056b1970bcbe09458ac05350a380a294a52c3650020` |

## Validação

O `verify:release` passou antes do empacotamento. O manifesto remoto confirmou `bundleId: production-286-2.3.56`, Web `2.3.56`, canal `production-286`, versionCode `286` e a mesma revisão de runtime.

O bundle baixado do deploy possui `16.156.920` bytes. O SHA-256 calculado localmente sobre o download é igual ao checksum do manifesto, e a assinatura RSA foi validada com a chave pública OTA.

A preflight `OPTIONS` da rota do manifesto respondeu HTTP 200 com `Access-Control-Allow-Origin: https://localhost`, métodos `GET, HEAD, OPTIONS`, `Access-Control-Allow-Headers: Content-Type` e `Vary: Origin`. Não foi usado wildcard.

## Aplicação

No APK HF208, deixe o app consultar a OTA com internet. Depois feche o app pela tarefa recente e reabra. A aplicação ocorre somente no próximo restart; não force reload durante login, uso ou viagem.

Nenhum APK foi gerado ou alterado neste deploy. A correção é exclusivamente Web/OTA.
