# NVU GTO/NVU R3.19 — Auditoria profunda e correção

## CAUSA RAIZ

1. **Fontes Java GTO duplicadas e divergentes.** Havia uma cópia de classes GTO no diretório raiz do projeto e outra no diretório Android realmente compilado. A cópia raiz continha métodos de conclusão/recuperação ausentes na versão compilada. Isso criava risco de corrigir uma implementação que não chegaria ao APK e, na versão compilada, havia chamadas para métodos inexistentes.

2. **Seleção visual sem garantia universal de toque.** O pipeline novo tinha seleção por toque, mas permaneciam caminhos legados e fallback de alteração visual que podiam promover uma linha sem uma evidência independente de toque.

3. **Fallback por proximidade/estado legado.** Funções antigas (`findFreightAt`/`findFreightFlexible`, probe visual legado e confirmação na saída da lista) permitiam que uma linha fosse atribuída sem uma transação de seleção inequívoca.

4. **Risco de baseline incorreto.** A seleção precisava de uma imagem anterior ao toque para comparar o estado pressionado. O fluxo atual foi fechado para nunca sintetizar esse baseline a partir de um frame arbitrário posterior.

5. **Snapshot podia ser reconstruído de `SharedPreferences` mutáveis.** O bloqueio usava o JSON `selectedFreight`, mas existia fallback para vários campos separados (`selectedCargo`, `selectedOrigin`, `selectedDestination`, etc.). Esses campos podem pertencer a outra viagem e misturar dados.

6. **Snapshot de sessão podia ser recriado.** A sessão podia reconstruir o contexto se o snapshot durável estivesse ausente. Isso quebra a cadeia de identidade; agora a ausência do snapshot bloqueia a viagem.

7. **Payload corrompia `origin`.** O código copiava `origin` e depois sobrescrevia o mesmo campo com `originCompany` no alias de compatibilidade.

8. **Backend não diferenciava corretamente todos os campos do frete.** O contrato foi ampliado para `origin`, `destinationCompany`, `rawText`, `selectedRow` e `freightFingerprint`, e o backend agora verifica o fingerprint contra os próprios campos recebidos.

9. **Máquina de estados aceitava gravações diretas e transições implícitas.** Foi adicionada uma guarda explícita de transições em `setTripState`, com `STATE_CONFLICT` quando uma transição impossível é tentada.

10. **Suíte de validação estava parcialmente desatualizada.** Alguns checks esperavam comportamentos antigos, como fallback de quadro único antes da primeira lista. A suíte foi atualizada para refletir o comportamento fail-closed atual e foi adicionada uma auditoria R3.19 ponta a ponta.

## CORREÇÕES APLICADAS

### Seleção exata
- seleção rápida somente dentro de janela correlacionada a toque;
- baseline precisa existir antes do toque;
- bounding box de `Aceitar` é a referência geométrica da linha;
- coordenadas, quando utilizáveis, precisam concordar com a linha visual;
- colisão/ambiguidade de bounding boxes falha fechado;
- alteração visual sem toque não promove frete;
- página diferente invalida OCR/cache anterior;
- o painel e geometria da seleção são congelados antes da mudança de estado;
- OCR preciso é protegido por geração e `sessionId`.

### Snapshot
- o snapshot durável é write-once por sessão;
- ausência do snapshot não é mais recuperada de preferências mutáveis;
- o bloqueio usa o `FreightOption` integral;
- todos os campos de identidade são preservados;
- `selectedRow` e `freightFingerprint` permanecem associados ao snapshot;
- uma tentativa de substituir um frete já bloqueado por outro é rejeitada.

### Estado e conclusão
- transições inválidas são bloqueadas;
- `Receber` é persistido com `commit()` antes da confirmação;
- resultado só vira concluído depois da confirmação normal;
- payload é selado localmente antes do envio;
- falha de backend mantém fila e snapshot;
- snapshot só é removido depois de ACK válido do backend;
- restart continua uma entrega concluída que ainda não recebeu ACK.

### Backend
- contrato GTO ampliado;
- validação de `origin`, `destination`, empresas e `selectedRow`;
- fingerprint do frete validado no servidor;
- correção do histórico para usar a origem canônica em `origem`;
- campos adicionais do GTO são preservados sem sobrescrever campos canônicos.

### Estrutura
- a cópia Java GTO conflitante da raiz foi retirada da árvore de fontes e preservada em `audit_archive/legacy-root-java/` apenas para auditoria histórica;
- o fluxo compilável fica concentrado em `android/app/src/main/java/com/nvu/operacional/`.

## ARQUIVOS ALTERADOS

- `android/app/src/main/java/com/nvu/operacional/GtoObserverService.java`
- `android/app/src/main/java/com/nvu/operacional/GtoAutoTripSync.java`
- `functions/src/gtoTrips.ts`
- `scripts/validate-gto-auto-sync.mjs`
- `scripts/validate-gto-native-flow.mjs`
- `scripts/audit-gto-r3-19-end-to-end.mjs`
- `package.json`
- `audit_archive/legacy-root-java/*` recebeu as cópias conflitantes antigas.

## TESTES REALIZADOS

- `audit:gto-r3.19-end-to-end`: **34/34 PASS**
- `audit:gto-r3.17-selection`: **19/19 PASS**
- `validate:gto-auto-sync`: **74/74 PASS**
- `validate:gto-native`: **47/47 PASS**
- `GtoSelectionCoordinatorTest`: **PASS**
- `node --check` nos scripts de auditoria/validação: **PASS**
- simulação das posições 1, 2, 3, 4 e 5: **PASS**
- toque fora das caixas: **PASS / rejeitado**
- bounding boxes sobrepostas: **PASS / rejeitado**
- fingerprint de dois fretes diferentes: **PASS / hashes diferentes**
- simulação completa LISTA → SELEÇÃO → SNAPSHOT → VIAGEM → RESULTADO → RECEBER → PAYLOAD: **PASS**

## BUILD

O build Android não pôde ser concluído neste ambiente porque o Gradle Wrapper exige `gradle-8.14.3-all.zip` e a máquina não conseguiu resolver `services.gradle.org` (`UnknownHostException`). Não foi gerado APK release neste ambiente e isso não é afirmado como concluído.

Também não foi possível executar `tsc` completo das Cloud Functions porque os diretórios `node_modules` não estão presentes e o ambiente não conseguiu recuperar os tipos externos necessários (`@types/node`).

## RESULTADO

O fluxo nativo foi fechado para **não enviar uma viagem quando a identidade do frete não puder ser comprovada**. O caminho de seleção agora depende de toque + correlação temporal/visual + geometria, e o envio usa exclusivamente o snapshot imutável da sessão.

A validação estática e a simulação de integridade estão verdes. O único bloqueio restante para certificação final do APK é executar o build em um ambiente com a distribuição Gradle 8.14.3 e dependências Android/Node disponíveis.
