# Relatório de liberação — Integridade de valores NVU Pro v1.2

**Data:** 16 de setembro de 2026  
**Canal nativo:** `production-358`  
**Versão Web:** `2.3.141`  
**APK:** `versionCode 358`, `versionName 1.0.358`  
**Package ID:** `com.nvu.operacional`

## Resumo executivo

Foi concluída a auditoria focalizada das discrepâncias de valores nos modos **Print, Pro e Max**. A causa foi confirmada em dois pontos independentes: o parser nativo do modo Pro aceitava um candidato OCR isolado e convertia formatos monetários ambíguos; no GTO Print, o OCR amplo podia substituir um valor já obtido pelo crop numérico dedicado. A persistência do Firestore não foi a causa primária: ela gravava o payload recebido e já possuía proteção de integridade contra alterações de valor por perfis não Senior.

A implementação foi aplicada antes da persistência. O painel Senior agora pode corrigir o valor no histórico; motoristas, administradores comuns e responsáveis de empresa continuam limitados à edição de rota. O valor corrigido é salvo em `valor` e `valorCents` no mesmo update, acompanhado da proveniência da correção Senior.

> O APK Release foi compilado, assinado com o certificado oficial NVU, validado com `versionCode=358` e publicado o pacote OTA no canal `production-358`. A validação automatizada passou; o teste físico em simuladores reais ainda depende da instalação no dispositivo do usuário.

## Causa raiz confirmada

| Fluxo | Causa confirmada | Consequência |
|---|---|---|
| Pro nativo | `parseCents` aceitava candidato único e inferia centavos a partir de texto OCR ambíguo. | Um token monetário válido sintaticamente, mas semanticamente incorreto, podia ser gravado imediatamente. |
| GTO Print | `promoteValue` permitia que OCR amplo promovesse outro candidato após o crop numérico. | Texto de diagnóstico podia sobrescrever a leitura principal. |
| TOE3/SCS/ATS Print | Leitura única do crop, sem a mesma proteção de múltiplas evidências. | Risco residual de OCR ruim, sem sobrescrita ampla no mesmo ponto do GTO. |
| Firestore | Não foi identificada como causa. `preservesTripValueIntegrity()` já restringe campos monetários e de proveniência ao claim `isSenior`. | O write é protegido por regra; valores incorretos precisavam ser impedidos antes do primeiro write. |

## Correções aplicadas

### Painel Senior

O modal de `TripHistory` mantém origem e destino para os fluxos já autorizados e exibe o campo **Valor recebido** somente quando `sessionStorage.seniorAccess === "true"`. O valor aceita entrada decimal brasileira, é normalizado com `parseTripValue`, arredondado para centavos e enviado como:

```text
valor = valorCents / 100
valorCents = valorCents
valorEditadoNoPainelSenior = true
valorEditadoPor = currentUser.id
valorEditadoEm = data da atualização
```

A regra Firestore continua sendo a autoridade de segurança: a função `preservesTripValueIntegrity()` só permite alteração monetária quando `isSenior()` é verdadeiro. A interface não substitui a regra de segurança.

### GTO Print

O OCR do crop numérico passou a ser marcado como fonte `numeric-crop`. O OCR amplo passou a ser `broad-diagnostic` e só pode servir como fallback quando nenhum crop produziu valor. Se houver candidato diferente após um valor já promovido, o candidato é ignorado e registrado como conflito; ele não pode sobrescrever o valor principal.

### Pro nativo

O parser nativo agora exige separador decimal explícito e exatamente dois dígitos de centavos. Ele rejeita valores sem separador, separadores incompletos, valores não positivos e valores fora do limite seguro. Quando a evidência não cumpre o contrato, o fluxo usa o fallback Web em vez de gravar um valor incerto.

### Max

O contrato do Max não foi duplicado nem substituído. O modo continua usando `GtoTripSubmissionCoordinator` e `GtoAutoTripSync`, com payload imutável, idempotência por sessão e confirmação do backend. A auditoria confirmou que a persistência do Max ocorre pela função `registerGtoTrip`, preservando a autoridade do fluxo existente.

## Validação executada

| Verificação | Resultado |
|---|---:|
| Teste focalizado de integridade de valores | **17/17 PASS** |
| TypeScript (`npx tsc --noEmit`) | **PASS** |
| Build Web (`npm run build`) | **PASS** |
| Build Android Release assinado | **PASS** |
| Print trip persistence | **PASS** |
| Pro receipt adapters | **PASS** |
| Pro native immediate registration | **PASS** |
| Pro fast registration contract | **PASS** |
| Pro consecutive background recovery | **PASS** |
| Trip consistency regressions | **PASS** |
| Pro operation terminal state | **PASS** |
| Operational completion coordinator | **PASS** |
| OTA readiness contract | **PASS** |
| Manifesto público, checksum e canal | **PASS** |

## Release e OTA

O manifesto público validado é:

`https://stirring-pavlova-ca6808.netlify.app/ota/production-358/manifest.json`

O pacote publicado possui `bundleId=production-358-2.3.141`, `nativeVersionCode=358`, `nativeChannel=production-358`, runtime `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR`, assinatura obrigatória e checksum SHA-256:

```text
77510ef0412b1445a62af49aef29c8c7310c6c6f2ea49984cdd0d3a8c27df344
```

O APK Release foi validado com assinatura APK v2 e certificado:

```text
Owner: C=br, ST=rj, L=rio de janeiro, O=nvuzzz, OU=nvuzz, CN=NVU
Certificate SHA-256: 806a03ea92b69e7f9a70526e9c4d6a4ae52ec4f9cf7cac05ddb1aa28084246ea
APK SHA-256: 82f97840d9c0aed2d151cc4b3b1fa277684401babb0821943fe0c1f3e00247e5
```

## Artefatos entregues

| Arquivo | Conteúdo |
|---|---|
| `NVU-Pro-v1.2-value-integrity-release.apk` | APK Release assinado e compatível com `production-358`. |
| `NVU-Pro-v1.2-value-integrity-ota.zip` | Bundle Web OTA assinado e publicado. |
| `NVU-Pro-v1.2-value-integrity-project-backup.zip` | Backup combinado dos projetos Web/Android, Dist, manifestos e notas. |
| `RELEASE_REPORT_VALUE_INTEGRITY_NVU_PRO_V1_2_2026-09-16.md` | Este relatório técnico. |

## Conclusão

A correção atende ao requisito de impedir que um OCR menos confiável substitua uma evidência monetária melhor e permite a correção controlada de registros no painel Senior. Print e Pro foram alterados nos pontos de validação anteriores à persistência; Max manteve seu coordenador e contrato de submissão. O canal OTA existente foi preservado e o novo bundle foi publicado com o mesmo certificado lógico, canal e runtime do APK atual.

