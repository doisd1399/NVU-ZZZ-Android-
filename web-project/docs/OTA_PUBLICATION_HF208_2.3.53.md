# Publicação OTA HF208 — Web 2.3.53

## Resultado

A implementação funcional do Last Known Good State foi aplicada, validada localmente, empacotada e publicada no site Netlify existente. A publicação atualiza a Dist Web e o bundle OTA no canal nativo `production-286`, mantendo o Android HF208 no versionCode 286.

| Item | Valor |
| --- | --- |
| Web version | `2.3.53` |
| OTA bundleId | `production-286-2.3.53` |
| Canal nativo | `production-286` |
| Android versionCode compatível | `286` |
| Runtime revision | `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR` |
| Site | `https://stirring-pavlova-ca6808.netlify.app` |
| Manifesto público | `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/manifest.json` |
| Bundle público | `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/nvu-live-update-2.3.53.zip` |
| Deploy ID | `6a9e0c3cd2809b29214f2834` |
| Bundle SHA-256 | `8beb0947bb50c3feebb20690243b5df030447085c02977d987751cbacb804af7` |

## Validações

A validação local aprovou `npm run verify:release`, incluindo lint, fluxo de login, ProfileIndex, Last Known Good State, teste funcional de dois perfis e build Web. O bundle foi validado criptograficamente com a chave pública local: assinatura RSA válida, checksum válido, ZIP íntegro e ausência de Android/keystore/`google-services.json`/`.env`.

A validação remota confirmou HTTP 200 no manifesto e no ZIP, `Access-Control-Allow-Origin: https://localhost`, métodos GET/HEAD/OPTIONS, `Vary: Origin`, checksum remoto idêntico ao local e manifesto com `nativeVersionCode: 286`.

## Aplicação no HF208

O APK não precisa ser recompilado. O WebView do HF208 deve consultar o manifesto, baixar `production-286-2.3.53`, fazer staging e aplicar somente no próximo restart, conforme o comportamento OTA já validado. Não force reload durante login, uso ou viagem. Feche o app pela tarefa recente e reabra para aplicar o bundle staged.

A publicação não altera regras de negócio, Firebase/OAuth, Firestore Rules ou Android nativo. O deploy via integração Netlify retornou 401, então foi usado o Netlify CLI já autenticado e vinculado ao mesmo site; o deploy final foi confirmado remotamente.
