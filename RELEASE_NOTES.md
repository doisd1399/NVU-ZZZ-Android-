# NVU Pro v1.2 — Correção de integridade de valores

Release gerado após auditoria focalizada de Print, Pro e Max.

| Área | Resultado |
|---|---|
| Painel Senior | Valor editável somente quando `isSenior` estiver presente na sessão; `valor` e `valorCents` são gravados juntos. |
| GTO Print | OCR do crop numérico é autoritativo; OCR amplo não pode sobrescrever candidato diferente. |
| Pro nativo | Candidatos sem separador decimal explícito, ambíguos ou fora do limite são enviados ao fallback Web. |
| Max | Persistência continua sob o coordenador nativo/Cloud Function idempotente; não houve alteração do contrato de submissão. |
| OTA | Manifesto publicado no canal `production-358`, com `nativeVersionCode=358` e runtime preservado. |
