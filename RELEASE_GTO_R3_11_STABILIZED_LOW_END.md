# NVU GTO R3.11 — v1.0.31 — Stabilized / Low-End Hardening

Base: R3.10 v1.0.30.

Correções desta revisão:
- callbacks assíncronos de OCR agora são vinculados à sessão/geração que os criou; resultados atrasados de uma viagem anterior são descartados e não podem preencher a viagem seguinte;
- geração de OCR da página de fretes tornou-se monotônica entre sessões para impedir reaproveitamento acidental de texto antigo;
- espera concorrente do OCR preciso ganhou limite seguro e fallback fail-closed;
- persistência de heartbeat, foreground e diagnóstico da lista de fretes foi reduzida sem reduzir a frequência do detector em memória;
- OCR textual de fundo da página de fretes foi espaçado, mantendo captura visual rápida e OCR preciso da linha selecionada;
- caches textuais persistidos da viagem anterior são limpos no reset;
- o ACK do Firebase é persistido localmente antes de remover fila/snapshot; se o armazenamento local falhar, a cópia durável permanece para retry idempotente.

O fluxo de integridade FIX18, o latch exato de Receber, a fila durável, idempotência Firebase e a recuperação da bolha permanecem preservados.
