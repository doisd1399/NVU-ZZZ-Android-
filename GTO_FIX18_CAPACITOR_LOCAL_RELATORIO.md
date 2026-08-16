# NVU GTO FIX18 — Capacitor / Android local

Base recebida: `NVU-Projeto-Completo-GTO-Etapa2.1-Registro-Automatico.zip`.

## Resultado

Este ambiente também precisava receber o FIX18, principalmente na camada nativa Android.

Correções aplicadas:
- snapshot imutável do contexto operacional por sessão;
- bloqueio do frete confirmado no snapshot da sessão;
- persistência síncrona da conclusão antes de qualquer limpeza/reset;
- fila durável selada por SHA-256;
- quarentena de entradas locais corrompidas em vez de descarte silencioso;
- migração de pendências FIX17;
- retry/backoff preservando a entrega concluída;
- remoção da fila somente após ACK FIX18 válido do Firebase;
- proteção contra nova sessão apagar entrega concluída ainda não selada/sincronizada;
- status de integridade/sincronização exposto ao bridge Capacitor;
- backend local espelhado do backend canônico do Google AI Studio para evitar divergência de contrato;
- `cap:sync:android` agora executa validação nativa e validação Android↔Firebase antes do build/sync.

Detector rápido preservado sem alteração:
- `GtoFastVisualDetector.java`: `069c51986dd6bdf58e2b8d12d1fdcb9862f35c3b24f27c30613241a6fe8ecbfd`
- `GtoSelectionCoordinator.java`: `d84fe0848f5a054225cf939786156c07a291a4eb74a362ca9f72878d920b0ddd`

Validações:
- fluxo nativo: 45/45;
- contrato Android↔Firebase: 74/74;
- auditoria final local FIX18: 26/26.

O ZIP final não inclui `node_modules`, `dist` antigo nem caches/builds Gradle. `google-services.json` e `meu-app.keystore.jks` foram preservados.
