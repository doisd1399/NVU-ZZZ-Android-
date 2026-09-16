# Publicação OTA HF208 — Web 2.3.54

## Resultado

A correção que remove o fallback inválido `Empresa vinculada` do selector foi aplicada, validada, empacotada e publicada no site Netlify existente. A Dist Web e a OTA foram atualizadas no mesmo deploy para o canal nativo `production-286`, mantendo o Android HF208 no versionCode 286.

| Item | Valor |
| --- | --- |
| Web version | `2.3.54` |
| OTA bundleId | `production-286-2.3.54` |
| Canal nativo | `production-286` |
| Android versionCode | `286` |
| Deploy ID | `6a9e0fc291abde57688fe36d` |
| Site | `https://stirring-pavlova-ca6808.netlify.app` |
| Manifesto | `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/manifest.json` |
| Bundle | `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/nvu-live-update-2.3.54.zip` |
| Bundle SHA-256 | `315c7b847597e9f0b20ca4934002960c6b0d3cb7df55d579dc88feb0e04654b2` |

## Validação

`npm run verify:release` passou na versão 2.3.54. O teste de ProfileIndex cobre empresas reais, Administrador/Motorista, memberships stale, simulador desconhecido e membership sem documento de empresa. O último caso permanece apenas em `invalidProfiles` e não entra no selector.

A verificação criptográfica confirmou assinatura RSA e checksum válidos. O manifesto remoto respondeu HTTP 200, mantém `nativeVersionCode: 286`, e o checksum do ZIP baixado remotamente coincide com o arquivo local. O CORS continua restrito a `https://localhost`, com GET/HEAD/OPTIONS e `Vary: Origin`.

## Aplicação na HF208

Abra o APK HF208 conectado à internet e permita que o manager OTA consulte o manifesto. A atualização deve ser staged e aplicada somente no próximo restart. Feche o app pela tarefa recente e reabra; não force reload durante login, uso ou viagem.

Nenhum APK foi gerado e Android nativo não foi alterado.
