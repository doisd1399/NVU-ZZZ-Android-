# Deploy HF208 — Web/OTA 2.3.58

## Identidade

- Canal nativo: `production-286`
- Bundle ID: `production-286-2.3.58`
- Web version: `2.3.58`
- Native versionCode: `286`
- Runtime: `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR`
- Deploy ID: `6a9f646acad74dbc4a486c45`
- URL pública: https://stirring-pavlova-ca6808.netlify.app
- URL imutável: https://6a9f646acad74dbc4a486c45--stirring-pavlova-ca6808.netlify.app

## Artefato

- Manifesto: https://stirring-pavlova-ca6808.netlify.app/ota/production-286/manifest.json
- Bundle: https://stirring-pavlova-ca6808.netlify.app/ota/production-286/nvu-live-update-2.3.58.zip
- SHA-256 remoto/local: `0b81027453159487763a0a099a141ae326af1409905ebfcd834e50dc0139d1d3`
- ZIP remoto: íntegro (`unzip -t` aprovado)
- Assinatura OTA: presente e obrigatória no manifesto

## Verificações

- Manifesto remoto HTTP 200.
- `bundleId`, `webVersion`, `nativeChannel` e `nativeVersionCode` correspondem à HF208.
- Checksum remoto coincide com o bundle local.
- CORS responde `Access-Control-Allow-Origin: https://localhost`.
- Métodos CORS: `GET, HEAD, OPTIONS`.
- `Vary: Origin` presente.
- Nenhum APK/AAB foi gerado ou alterado neste deploy.
- Android nativo, package, versionCode e mecanismo OTA não foram modificados.

## Aplicação

O APK HF208 existente deve consultar o manifesto com internet, fazer staging e aplicar o bundle somente no próximo restart. Fechar pela tarefa recente e reabrir; não forçar reload durante login, uso ou viagem.
