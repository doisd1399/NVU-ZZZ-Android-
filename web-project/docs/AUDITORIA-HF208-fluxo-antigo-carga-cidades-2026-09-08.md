# Auditoria HF208 — fluxo antigo de carga e cidades

## Escopo

A auditoria compara o contrato operacional atual — selecionar o frete e, em seguida, escolher manualmente apenas origem e destino — com o código nativo Android HF208 atualmente presente no projeto.

## Contrato novo confirmado

Depois de um toque humano confirmado no card `Aceitar`, o sistema deve preservar o row, km e valor do mesmo card, abrir diretamente a lista manual de origem, depois a lista manual de destino e selar a viagem somente após as duas escolhas. Cargo, identificação automática de cidades e formulário genérico de revisão não podem ser requisitos ou superfícies do motorista nesse caminho.

## Achados comprovados

### 1. O caminho novo existe, mas não é a única porta de entrada

`beginManualRouteSelection()` limpa cargo, origem, destino, empresas e texto OCR do draft, preserva km/valor do card e define `manualRouteSelectionPending=true` e `manualRouteSelectionStep=ORIGIN`. `applyManualRouteSelection()` conduz ORIGIN → DESTINATION e chama `commitReviewedFreight()` após a segunda escolha. Esse é o caminho correto.

Entretanto, `enterFreightReview()` ainda contém uma bifurcação legada completa. Se o caminho manual não for alcançado, ele grava `pendingFreightReview=true`, `reviewRequiredField`, `reviewCargo`, `reviewOrigin`, `reviewDestination`, `reviewKm` e `reviewValue`, podendo abrir o formulário genérico “Confirme somente ...” e “Salvar ...”.

### 2. O prompt observado é gerado pelo formulário genérico legado

A renderização do menu em `GtoObserverService.java` mantém o ramo que, quando `manualRouteSelectionPending` é falso, lê `reviewRequiredField` e monta um `EditText` com o texto “Confirme somente <campo>.” e um botão “Salvar <campo>”. Quando o campo é antigo, vazio ou desconhecido, `reviewFieldLabel()` retorna “campo”, produzindo exatamente “Confirme somente campo.”/“Salvar campo”.

Esse ramo é incompatível com o contrato novo e continua executável.

### 3. Cargo ainda participa de gates de revisão e de recuperação

`GtoFreightReviewPolicy` mantém `CARGO` como campo obrigatório potencial. `firstRequiredField()` ainda considera cargo, origem, destino, distância e valor. `GtoFreightReviewEligibilityPolicy` calcula elegibilidade com cinco campos, incluindo cargo, e `GtoAutoTripSync` ainda bloqueia o lock quando `cargoVotes`/evidência de cargo não atendem o consenso, salvo bypasses específicos.

No fluxo manual novo, cargo é apagado do draft e não deve bloquear a rota; portanto, esse conjunto precisa ser retirado da decisão ativa de seleção/rota. Cargo pode permanecer somente como metadado compatível do registro, sem ser lido, solicitado ou usado como gate.

### 4. O caminho Pause ainda lê e pode pedir cargo

O caminho de recuperação de Pause chama `pauseTextField(..., "carga", "carga transportada", "mercadoria")`, registra consenso de cargo, usa `pauseMissingField()` e pode retornar `GtoFreightReviewPolicy.CARGO`. `armPauseManualFallback()` pode transformar esse campo em revisão manual. A leitura de Pause também usa `GtoCityTextResolver` para canonicalizar destino e `pauseLocationTextField()` para inferir cidades.

Esse caminho é logicamente antigo para a nova seleção de rota. Ele não deve ser chamado como continuação de um novo frete selecionado. Se for mantido para compatibilidade de viagens históricas, deve ficar isolado de `manualRouteSelectionPending` e não poder alterar ou bloquear a transação atual.

### 5. A identificação automática de cidades ainda está ativa em várias camadas

`GtoCityTextResolver`, `GtoKnownDestinationPolicy`, `canonicalizeSelectedListDestinationSpelling()`, `canonicalizePauseDestination()` e parsers de localização são usados para transformar OCR em cidades/canonicalizações. Isso conflita com a regra nova quando a escolha de origem/destino deve vir exclusivamente da lista manual fechada.

`GtoManualRouteSelectionPolicy` já fornece a lista autorizada e valida igualdade literal. Ela deve ser a única autoridade de origem/destino no ciclo novo. A resolução OCR de cidades pode ser preservada apenas para leitura histórica/diagnóstico isolado, sem escrever `reviewOrigin`, `reviewDestination`, `manualRouteSelectionStep`, `selectedOrigin` ou `selectedDestination` da transação nova.

### 6. Há projeções de compatibilidade que não devem ser confundidas com autoridade

`GtoCertifiedFreight`, `GtoAutoTripSync` e `GtoObserverDiagnostics` ainda armazenam campos `cargo`, `selectedCargo*`, `cargoVotes`, `originCompany` e `destinationCompany`. Esses campos podem continuar no payload por compatibilidade e auditoria, mas não devem decidir se a seleção manual pode prosseguir.

A autoridade do novo ciclo deve ser somente: `selectionAttemptId`, `selectedFreightRow`, `freightPageGeneration`, snapshot do card com km/valor, `manualRouteSelectionStep`, `reviewOrigin`, `reviewDestination` e `manualRouteSelectionConfirmed`.

## Causa raiz

O comportamento observado não é um simples problema de OCR. A causa é a coexistência de dois contratos no mesmo serviço: o contrato novo de rota manual e o contrato antigo de revisão campo a campo. Quando uma leitura fica incompleta, um callback legado pode definir `reviewRequiredField`/`pendingFreightReview` ou acionar Pause antes que o caminho manual seja iniciado. A UI então abre o formulário antigo e o motorista vê uma solicitação de carga/campo, apesar de o novo fluxo já ter sido definido.

## Correção segura proposta

1. Criar um guard único `isNewManualRouteSelectionCycle()` para qualquer seleção humana de frete atual.
2. Depois de `hasConfirmedSelectionIdentity()`, sempre iniciar `beginManualRouteSelection()`; nunca chamar o formulário genérico para esse ciclo.
3. Fazer `firstReviewField()` retornar apenas estados internos de dados pendentes ou as etapas manuais de rota, nunca `CARGO`, `ORIGIN`, `DESTINATION`, `DISTANCE` ou `VALUE` como entrada de formulário.
4. Bloquear `applyManualFreightReviewField()` para campos legados e redirecionar o ciclo para a lista manual de origem/destino.
5. Desacoplar Pause/OCR de cidade/carga da transação nova: esses caminhos não podem escrever nem sobrescrever a rota manual atual.
6. Alterar o lock do `GtoAutoTripSync` para que, quando `manualRouteSelectionConfirmed=true` e row/generation/fingerprint estiverem ligados, cargo seja opcional e nunca gate de seleção.
7. Manter cargo e empresas somente como campos compatíveis do snapshot/payload, sem pedir ao motorista e sem autoridade sobre origem/destino.
8. Limpar estados antigos (`reviewRequiredField`, `pendingFreightReview`, `reviewCargo`, `pauseMissingField`, `pauseManualFallbackAllowed`) ao abrir uma nova lista/frete, preservando apenas a seleção atual e seus dados.

## Critério de aprovação

Com um frete selecionado, a única superfície de interação deve ser `Etapa 1/2 · Origem` e depois `Etapa 2/2 · Destino`. Não deve aparecer “carga”, “Confirme somente ...”, “Salvar campo”, formulário de texto ou identificação automática de cidade. Km e valor devem vir do snapshot do card; ausência deles deve manter a transação em validação/releitura, nunca pedir cargo ao motorista.

Nenhum APK ou OTA foi alterado durante esta auditoria.
