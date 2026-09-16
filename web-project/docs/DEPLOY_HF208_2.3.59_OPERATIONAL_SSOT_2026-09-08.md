# Deploy HF208 — Web/Dist/OTA 2.3.59

## Identidade publicada

A Dist Web e a OTA da base HF208 foram publicadas no mesmo site Netlify e no canal `production-286`.

| Campo | Valor |
| --- | --- |
| Web | `2.3.59` |
| Bundle ID | `production-286-2.3.59` |
| Native versionCode | `286` |
| Runtime | `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR` |
| Deploy ID | `6a9fcfa5089dca66654a9a7b` |
| URL pública | `https://stirring-pavlova-ca6808.netlify.app` |
| URL imutável | `https://6a9fcfa5089dca66654a9a7b--stirring-pavlova-ca6808.netlify.app` |
| Manifesto | `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/manifest.json` |
| Bundle | `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/nvu-live-update-2.3.59.zip` |
| SHA-256 remoto/local | `94a2fe790ff06b20a570d55120571cb50437a524cf1fb821cac021a0460d452f` |
| Bundle bytes | `16183374` |

## Verificações

O `verify:release` passou antes do empacotamento. A validação remota confirmou manifesto HTTP 200, bundle ID correto, Web 2.3.59, canal production-286, versionCode nativo 286, assinatura RSA válida, checksum igual ao local e ZIP íntegro.

O CORS da rota do manifesto respondeu `Access-Control-Allow-Origin: https://localhost`, métodos `GET, HEAD, OPTIONS`, header `Content-Type` e `Vary: Origin`. Não foi utilizado wildcard.

Nenhum APK foi gerado ou alterado durante este deploy. O APK HF208 imutável continua separado: a atualização publicada altera apenas a camada Web/OTA pública. No APK novo, o consumo OTA nativo está bloqueado e o manifesto embutido declara `nativeBundleImmutable:true`.
