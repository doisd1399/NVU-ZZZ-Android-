# NVU GTO R3.17 — Auditoria e correção da seleção exata de frete

## Resultado

A causa raiz não era um único timeout. O fluxo tinha caminhos de fallback que podiam promover um frete sem provar que a linha visual tocada era a linha selecionada.

Os problemas críticos encontrados foram:

1. **Seleção por aproximação vertical**
   Existiam resolvers legados (`findFreightAt`/`findFreightFlexible`) que podiam escolher a linha mais próxima da coordenada, inclusive com tolerância/expansão de área. Isso viola a regra de nunca escolher por aproximação.

2. **Fallback de uma única imagem**
   O detector podia inferir a linha selecionada usando somente um frame posterior, procurando um outlier de alteração da cor laranja. Sem um baseline comprovadamente anterior ao toque, isso não prova qual linha foi tocada.

3. **Hitbox de seleção ampliada**
   O caminho preciso ampliava o bounding box do botão `Aceitar` em 10 dp. Isso permitia que um toque fora da área real fosse atribuído a uma linha.

4. **Baseline pré-toque artificial**
   Quando não havia frame comprovadamente anterior ao toque, o código podia usar o último frame disponível como baseline. Isso transforma uma ausência de evidência em evidência de seleção.

5. **Coordenadas de toque não eram usadas como evidência independente de identidade**
   A coordenada podia estar disponível no evento `ACTION_OUTSIDE`, mas não era confrontada de forma estrita com as caixas reais dos botões detectados.

6. **Snapshot não preservava toda a identidade do `FreightOption`**
   O lock durável reconstruía um objeto a partir de poucos campos selecionados (`cargo`, origem, destino, km e valor). Isso deixava espaço para perder campos existentes no modelo ou misturá-los em uma reconstrução posterior.

7. **Ausência de um identificador imutável do frete bloqueado**
   O snapshot não tinha um fingerprint criptográfico do conjunto completo de campos do frete selecionado. A comparação de integridade era apenas por alguns campos.

## Correção aplicada

### Seleção exata

O fluxo de seleção agora exige, em conjunto:

- marcador de toque associado ao evento;
- baseline comprovadamente anterior ao toque;
- detecção visual de alteração da linha após o toque;
- coordenada do toque, quando o Android/OEM disponibiliza uma coordenada utilizável;
- correspondência da coordenada com **uma única** bounding box real do botão `Aceitar`;
- concordância entre a linha visual e a linha indicada pela coordenada, quando a coordenada é utilizável;
- fechamento/desaparecimento da lista como confirmação de que a seleção foi consumida;
- OCR preciso da linha selecionada;
- validação contra o mesmo `rowIndex`, protegida por geração da página e sessão.

Quando qualquer evidência obrigatória não é confiável, a seleção é bloqueada. Não existe mais fallback para primeiro/último/mais próximo/outlier de uma única imagem.

### Snapshot imutável

Após a validação da mesma linha:

- o JSON completo de `FreightOption` vira a fonte do lock;
- campos existentes são preservados: carga, rota/empresa, origem, empresa de origem, destino, empresa de destino, distância, valor, texto bruto e linha selecionada;
- o snapshot recebe `freightFingerprint` SHA-256;
- `sameFreight()` usa o fingerprint para impedir substituição por outro frete;
- o snapshot é mantido até o caminho de ACK confirmado;
- somente depois de ACK local persistido e remoção segura da fila o snapshot é removido.

### Envio após conclusão

O payload da fila passa a carregar também a identidade bloqueada do frete, inclusive o fingerprint. A viagem continua sendo selada antes do envio pela fila durável.

## Arquivos alterados

- `android/app/src/main/java/com/nvu/operacional/GtoObserverService.java`
- `android/app/src/main/java/com/nvu/operacional/GtoFastVisualDetector.java`
- `android/app/src/main/java/com/nvu/operacional/GtoAutoTripSync.java`
- `android/app/build.gradle` — versão Android atualizada para `35 / 1.0.35`
- `package.json` — novo comando `audit:gto-r3.17-selection`
- `scripts/audit-gto-r3-17-selection-integrity.mjs` — auditoria estática dedicada adicionada

`GtoSelectionCoordinator.java` foi auditado e continua fornecendo a ordenação de frames/marcadores por sequência; não precisou ser alterado.

## Validação

### Auditoria dedicada R3.17

**19/19 checks passaram**, cobrindo:

- remoção de seleção por uma única imagem;
- remoção de nearest-row;
- bounding box exata;
- ausência de hitbox ampliada no seletor de frete;
- baseline obrigatoriamente pré-toque;
- concordância das coordenadas quando utilizáveis;
- correlação visual depois do toque;
- confirmação pelo fechamento da lista;
- congelamento de painel/linha da transação;
- proteção por sessão/geração;
- snapshot completo do `FreightOption`;
- fingerprint SHA-256;
- imutabilidade do lock;
- remoção do snapshot somente no caminho de ACK;
- fingerprint presente na fila;
- ação `Receber` exata;
- selagem antes da rede.

### Teste do coordenador

`GtoSelectionCoordinatorTest`: **PASS**.

### Build Android / APK

O build Gradle não pôde ser concluído neste ambiente porque o wrapper precisa baixar o Gradle `8.14.3` e o ambiente não tem acesso de rede a `services.gradle.org` (`UnknownHostException`). Não seria correto afirmar que um APK release foi gerado sem executar o build real.

O projeto corrigido permanece pronto para build em uma máquina com a distribuição Gradle disponível/cacheada.
