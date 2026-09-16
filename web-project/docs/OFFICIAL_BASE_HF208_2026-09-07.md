# Base oficial NVU — HF208

## Declaração de fonte única

A partir desta entrega, a fonte oficial de trabalho do NVU é o projeto local:

`/home/ubuntu/work/nvu-web24-local-ota`

A identidade nativa oficial é:

| Campo | Valor oficial |
| --- | --- |
| Aplicativo | `com.nvu.operacional` |
| HF | `HF208` |
| Android versionCode | `286` |
| Android versionName | `1.0.286` |
| Canal nativo | `production-286` |
| Runtime | Capacitor local |
| Runtime revision | `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR` |
| Assinatura Release | Certificado compatível já validado localmente |
| Debug diagnóstico | Mesma identidade de pacote/certificado, `DEBUGGABLE` ativo |

## Estado Web/OTA no congelamento

O projeto local contém a implementação Web atual com as correções de hidratação imediata, CompanyTab, perfil de motorista, histórico, cache e distinção entre dados existentes e refresh. O `package.json`, `dist/nvu-build.json` e artefatos locais estão em Web `2.3.55`.

A versão pública consultada no manifesto Netlify continua sendo `production-286-2.3.54`. Portanto, `2.3.55` é o candidato Web/OTA da base oficial HF208 para esta entrega, não uma afirmação de publicação pública já realizada.

## Regras de paridade

Toda futura alteração Web, APK Release, APK Debug, Dist ou OTA deve partir deste projeto congelado, manter `com.nvu.operacional`, versionCode `286`, canal `production-286` e a mesma revisão de runtime, salvo autorização explícita para criar uma nova base.

A camada Android deve embutir a Dist produzida a partir desta mesma base antes de qualquer compilação. A OTA deve ser gerada a partir da mesma Dist e do mesmo `package.json` Web. Não devem ser aceitos APKs com Web embutida em versão diferente da Dist/OTA oficial do pacote.

## Segurança e escopo

Keystore, senhas, `google-services.json`, `.env`, `local.properties`, tokens e chaves privadas permanecem fora dos pacotes de backup. Regras Firebase/Firestore, permissões, camada nativa e lógica de negócio não devem ser alteradas apenas para alinhar versões.

Esta declaração fixa a base de trabalho até nova autorização explícita do usuário.
