# Deploy HF208 — Dist Web e OTA 2.3.55

## Resultado

A Dist Web e a OTA foram publicadas no site Netlify oficial da HF208, no canal nativo `production-286`.

| Campo | Valor |
| --- | --- |
| Web | `2.3.55` |
| Bundle OTA | `production-286-2.3.55` |
| Native versionCode | `286` |
| Runtime revision | `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR` |
| Site | `stirring-pavlova-ca6808.netlify.app` |
| Deploy ID | `6a9e9e72157080fd50711f0b` |
| Deploy imutável | `https://6a9e9e72157080fd50711f0b--stirring-pavlova-ca6808.netlify.app` |
| Bundle remoto | `16,156,811` bytes |
| Checksum remoto | `f56426e0a8b610ac958bbcff4fce8e0e26ff9c20881dc11b786af8874f3d274c` |

## URLs

- Aplicação Web: `https://stirring-pavlova-ca6808.netlify.app`
- Manifesto: `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/manifest.json`
- Bundle: `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/nvu-live-update-2.3.55.zip`
- Build manifest: `https://stirring-pavlova-ca6808.netlify.app/nvu-build.json`

## Verificações remotas

O manifesto remoto confirmou `bundleId: production-286-2.3.55`, `webVersion: 2.3.55`, `nativeChannel: production-286`, `nativeVersionCode: 286`, `signatureRequired: true` e a revisão de runtime oficial.

O SHA-256 calculado sobre o bundle baixado foi igual ao checksum do manifesto. A assinatura RSA foi validada com a chave pública OTA. O build manifest remoto retornou Web `2.3.55`, runtime local e a mesma revisão oficial.

A preflight `OPTIONS` e a resposta `GET` do manifesto retornaram HTTP 200 com CORS estrito para `https://localhost`, métodos `GET, HEAD, OPTIONS`, header `Content-Type` e `Vary: Origin`. Não foi usado wildcard `*`.

## Aplicação no HF208

O APK HF208 existente deve consultar/stagear a atualização com internet. A aplicação do bundle ocorre somente no próximo restart conforme a política Live Update. Feche o app pela tarefa recente e reabra; não force reload durante login, uso ou viagem.

Nenhum APK foi gerado nesta publicação e a camada Android não foi alterada.
