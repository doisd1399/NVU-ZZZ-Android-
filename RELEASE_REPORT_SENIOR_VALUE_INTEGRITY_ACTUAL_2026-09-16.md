# Relatório de release — Integridade de valores e edição Senior

**Data:** 16 de setembro de 2026  
**Package ID:** `com.nvu.operacional`  
**Version code:** `358`  
**Versão:** `1.0.358`  
**Branch:** `audit/senior-value-auth-parity`  
**Commit:** `2dc553ee5fdab1a93f8859ade3d3539aa484bc4e`

## Estado da implementação

A implementação das correções de integridade de valores já estava presente no pacote recebido e foi verificada no código Web e Android. O `TripHistory` usa `isSeniorAuthenticated`, validado pela sessão Firebase, como fonte principal de autorização; `sessionStorage.seniorAccess` permanece apenas como compatibilidade do preview de empresa. O campo `Valor recebido` grava `valor` e `valorCents` juntos, com os metadados de correção Senior.

O GTO Print promove o crop numérico como fonte `numeric-crop` e rejeita sobrescrita divergente do OCR amplo. O parser nativo Pro exige separador decimal explícito, exatamente dois dígitos de centavos e rejeita valores ambíguos ou fora do limite, usando fallback Web quando a evidência não atende ao contrato. As regras Firestore preservam a integridade monetária e restringem a alteração de valor ao claim Senior.

## Validação

| Verificação | Resultado |
|---|---:|
| Teste de autorização Senior | **PASS 4/4** |
| Teste focalizado de integridade de valores | **PASS 17/17** |
| TypeScript (`npm run lint`) | **PASS** |
| Build Web | **PASS** |
| Coordenador de conclusão operacional | **PASS** |
| Imutabilidade OTA nativa | **PASS** |
| Regressões de consistência | **PASS** |
| Build Android Release | **PASS**, assinatura oficial NVU verificada |
| Build Android Debug | **PASS**, assinatura APK v2 válida |

## Artefatos

### APK Debug instalável

Arquivo: `NVU-Pro-v1.2-senior-auth-parity-debug.apk`  
SHA-256: `f7ef1d5c5dbf9710a5a4536819c8905034dff6d5f2598d30cf94a3460964244a`  
Assinatura: debug keystore, APK Signature Scheme v2 verificada.

### APK Release assinado

Arquivo: `NVU-Pro-v1.2-senior-auth-parity-release.apk`  
SHA-256: `a77efea8849c1bdc950366d7ddb1370e155a6112f7ed83bca1767a8fec635a65`  
Identidade: `com.nvu.operacional`, version code `358`, versão `1.0.358`.  
Assinatura: APK Signature Scheme v2/v3 verificada. Certificado SHA-256 `806a03ea92b69e7f9a70526e9c4d6a4ae52ec4f9cf7cac05ddb1aa28084246ea`.

## Segurança e backup

O `google-services.json`, keystores, senhas, `.env` e propriedades locais não fazem parte do backup nem do Git. O arquivo Firebase e a keystore oficial foram usados somente durante o build/assinatura local e removidos ao final. O backup contém o código-fonte Web/Android, documentação, manifesto e `dist` sem dependências, intermediários ou credenciais.
