# NVU GTO R3.10 — Full Flow / Slow Device Hardening

Versão Android: 1.0.30 (versionCode 30)

## Ajustes adicionais sobre R3.9
- Janela de confirmação do Observador ampliada para aproximadamente 5 s em cold start.
- Janela de confirmação do MediaProjection ampliada para aproximadamente 8 s.
- Retorno automático ao GTO após reautorização aguarda aproximadamente 8 s antes de declarar falha.
- Abertura do GTO usa REORDER_TO_FRONT para reutilizar a tarefa existente quando possível.
- Retry da bolha reduzido para 350 ms; overlay desconectado pelo OEM zera o throttle e pode ser recriado no mesmo ciclo de foreground.
- Recuperação pós-permissão ganhou tentativas adicionais em 4,2 s e 6,5 s.
- Grace de retorno da permissão ampliado para aparelhos/OEMs lentos.

## Certificação adicionada
- audit:gto-r3.10: invariantes de ponta a ponta, persistência, seleção, resultado, fila, ACK e recuperação.
- test:gto-r3.10-stress: simulação determinística de 1.500 operações e 14.279 viagens consecutivas, com falhas/retries, reinícios, perda de captura e desconexão de overlay.

Nenhuma alteração foi feita no contrato FIX18, nome/region da Cloud Function, regra de idempotência ou semântica dos dados registrados.


## Resultado final
Toda a suíte R3.3–R3.10, FIX18, fluxo nativo, auto-sync e navegação Sênior passou. Consulte `R3_10_AGGRESSIVE_VALIDATION_REPORT.txt` para os números completos e a limitação de teste físico/OEM.
