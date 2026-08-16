# NVU GTO R3.16 — Auditoria final de release

## Escopo
Auditoria estática e correção segura do fluxo automático GTO no projeto Web/Netlify + Capacitor/Android.

## Fluxo auditado
1. Web prepara contexto da operação e chama `launchGtoWork`.
2. Capacitor valida plataforma, permissões, operação aberta e observador.
3. Observador Android é iniciado/reidratado.
4. MediaProjection é autorizada enquanto a NVU está visível.
5. O GTO só é aberto depois de `projectionActive=true`.
6. O serviço captura a tela e detecta a lista de fretes.
7. A lista entra em ciclo de vida: visível → fechada → reaberta.
8. O toque é correlacionado por sequência de frames.
9. O snapshot pré-toque é congelado antes da mudança de tela.
10. OCR preciso lê somente a linha selecionada e pode usar apenas o snapshot estabilizado da mesma linha como fallback.
11. Conflitos de KM/valor entre leituras bloqueiam a seleção em vez de adivinhar.
12. O frete é persistido e bloqueado no snapshot imutável da sessão.
13. A viagem entra em `TRIP_IN_PROGRESS`.
14. Durante a rota, o detector visual procura a tela de conclusão e usa OCR apenas quando necessário.
15. `Receber` normal é tratado como evento durável; bônus/anúncio é bloqueado como conclusão normal.
16. A entrega confirmada é selada em fila local e enviada ao backend com idempotência por sessão.
17. Falhas de rede/autenticação preservam o payload para retry.

## Correção crítica R3.16 → release
Foi identificado um race condition silencioso no ciclo de reabertura da lista:

- o GTO podia fechar a lista enquanto o estado ainda era `CONFIRMING_FREIGHT`;
- a confirmação/OCR falhava depois;
- o serviço voltava para `WAITING_FREIGHT`;
- o fechamento anterior não armava `freightListReopenPending` porque a função antiga só armava o pending quando já estava em `WAITING_FREIGHT`;
- uma lista visualmente idêntica podia então ser interpretada como a mesma tentativa.

### Correção
A R3.16 adiciona `armFreightListReopenAfterSelectionFailure()` e registra o fechamento antes de restaurar `WAITING_FREIGHT`. Depois da falha, a próxima abertura confirmada:

- incrementa o ciclo de seleção;
- descarta o snapshot da sessão anterior;
- limpa OCR/cache/snapshot/seleção anterior;
- cria novo `gtoTripSessionId`;
- cria novo snapshot de contexto;
- força uma nova geração de OCR;
- não reutiliza dados da tentativa anterior.

Uma lista que permanece aberta não cria novas sessões por frame.

## MediaProjection
A permissão de captura é tratada como transição técnica:

- foreground do GTO tem grace period;
- callbacks antigos são protegidos por `projectionGeneration`;
- reinício da projeção limpa apenas contadores transitórios de ausência;
- sessão, frete bloqueado e estado lógico não são apagados;
- GTO não é aberto antes de `projectionActive=true`;
- não existe captura de áudio.

## Fail-closed
Em caso de dúvida, o sistema prefere não registrar a viagem. São bloqueados:

- OCR insuficiente;
- conflito entre leituras da mesma linha;
- sessão inexistente;
- snapshot inválido;
- UID diferente do motorista autenticado;
- payload corrompido;
- operação encerrada;
- falha de persistência local;
- resposta backend incompatível com a sessão.

## Validações executadas
- R3.16 release audit: **22/22 PASS**
- Native GTO flow: **47/47 PASS**
- Auto trip sync contract: **74/74 PASS**
- GTO modes/print regression: **20/20 PASS**
- Deterministic retry model: **PASS**

## Limitação de build
A compilação Gradle final não pôde ser executada neste ambiente porque o Gradle Wrapper 8.14.3 não está disponível localmente e o ambiente não possui acesso DNS/Internet para baixá-lo (`UnknownHostException: services.gradle.org`).

Portanto, o pacote é estruturalmente auditado e corrigido, mas o build Android final deve ser executado no Windows/Android Studio com o SDK/Gradle disponíveis.

## Estado de release
**R3.16 — código preparado para release, sujeito ao build Android local e teste físico no aparelho.**
