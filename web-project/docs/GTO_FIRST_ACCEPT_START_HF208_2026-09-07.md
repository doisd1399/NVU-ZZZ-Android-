# HF208 — ACEITAR → nova sessão GTO

## Resultado

A implementação nativa foi corrigida para que o primeiro ACEITAR correlacionado a uma linha concreta crie a sessão operacional imediatamente, sem aguardar frame estável, refresh de Projection Surface, OCR completo, ACK Firebase ou qualquer segunda tentativa.

A nova transação é idempotente e persiste `selectionTransactionId`, `selectionTransactionSessionId`, `selectionTransactionRow`, `selectionTransactionStatus`, `selectionEvidenceStatus`, `selectionExpectedDistanceKm` e `selectionProvisionalFreightId`. O estado intermediário `TRIP_STARTED` não libera conclusão, envio ou resultado; esses caminhos continuam exigindo validação do frete.

A evidência mínima posterior é `ACEITAR + KM` do mesmo `rowIndex`. KM divergente, card diferente, ambiguidade e callback antigo não validam a seleção e não alteram a sessão atual. O handoff da viagem anterior somente sela a fila e marca `gtoNextTripPending`; a sessão seguinte nasce no primeiro ACEITAR.

## Verificações

| Verificação | Resultado |
| --- | --- |
| Gate ACEITAR/sessão/evidência | PASS — cenários A–J |
| Gate handoff | PASS — 7 cenários |
| Gate first-next-freight | PASS — 8/8 |
| TypeScript/lint | PASS |
| Compilação Java Debug | PASS |
| `verify:release` | PASS |
| APK Release | Compilado e assinado |
| Web embutido | 2.3.57 |
| ZIP APK | Integridade PASS |
| Teste físico no Motorola | Ainda não executado |

## Identidade do APK

- Package: `com.nvu.operacional`
- VersionCode: `286`
- VersionName: `1.0.286`
- Variante: Release, não debuggable
- SHA-256: `85844739884747004f7421e82d2282b00457bcce672e6286c38566967de24501`
- Web/runtime: `2.3.57` / `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR`

## Limites

A validação automatizada e a compilação não comprovam sozinhas o comportamento físico no Motorola. O teste necessário é concluir uma viagem, observar o primeiro ACEITAR da próxima viagem, testar sem rede, repetir o toque e verificar que ACK atrasado não interfere. Nenhuma publicação Web/OTA foi executada nesta etapa.
