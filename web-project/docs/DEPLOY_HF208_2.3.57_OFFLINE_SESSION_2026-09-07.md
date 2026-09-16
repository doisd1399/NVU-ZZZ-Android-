# Deploy HF208 — sessão offline 2.3.57

## Resultado

**Deploy concluído e validado.** A implementação de sessão offline da base HF208 foi publicada como Dist Web 2.3.57 e bundle OTA `production-286-2.3.57`. Nenhum APK foi gerado nesta execução.

## Identidade

| Campo | Valor |
| --- | --- |
| Site | `stirring-pavlova-ca6808.netlify.app` |
| Canal OTA | `production-286` |
| Bundle ID | `production-286-2.3.57` |
| Web version | `2.3.57` |
| Native version code | `286` |
| Runtime | `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR` |
| Bundle SHA-256 | `50c637fd490ff95dbaff07d206d1408be49166fc68fff705638d88fa6b0135f9` |

## URLs

- Web: `https://stirring-pavlova-ca6808.netlify.app`
- Manifesto: `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/manifest.json`
- Bundle: `https://stirring-pavlova-ca6808.netlify.app/ota/production-286/nvu-live-update-2.3.57.zip`

## Verificações

O manifesto público respondeu HTTP 200 e apontou para `production-286-2.3.57`, Web 2.3.57, versionCode nativo 286 e o checksum acima. O ZIP remoto foi baixado integralmente, apresentou exatamente 16.182.283 bytes, coincidiu com o checksum local e passou `unzip -t`. A assinatura RSA foi validada com a chave pública oficial já configurada para a HF208.

O CORS remoto respondeu `Access-Control-Allow-Origin: https://localhost`, métodos `GET, HEAD, OPTIONS`, `Access-Control-Allow-Headers: Content-Type` e `Vary: Origin`. Não foi usado wildcard `*`.

## Limites

A OTA é aplicada somente no próximo restart do app. Não é necessário gerar APK para esta atualização. O teste físico offline da sessão ainda deve ser realizado no HF208 após o staging: abrir com rede, permitir que o snapshot seja gravado, fechar pela tarefa recente, reabrir sem rede, conferir selector/perfil/empresa e então restaurar a rede para observar a reconciliação.
