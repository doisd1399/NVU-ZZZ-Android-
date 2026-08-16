# NVU GTO R3.2 — UI e fallback de conclusão

## Correções

- Removida a orientação duplicada exibida abaixo dos dados da viagem. O guia de etapa no topo permanece como fonte única de orientação durante `TRIP_IN_PROGRESS`.
- Corrigida a causa do aviso prematuro de falha na finalização automática. `GtoResultVisualGate` continua permissivo apenas para acordar OCR, mas um candidato visual isolado não pode mais habilitar fallback nem avisar o motorista.
- O fallback **Confirmar conclusão da entrega** só é disponibilizado após OCR repetido encontrar evidência semântica real da tela de resultado (`Valor a receber` ou `Concluído` combinado com ação de recebimento/ADS) e ainda assim não conseguir montar o resultado completo.
- Mantida a detecção automática da tela `Concluído`, captura do valor, confirmação de `Receber`, fila durável e envio automático ao Firebase.
- Incluída a correção de compilação `MainActivity.onStart()` como `public`, compatível com a versão atual de `BridgeActivity`.
- Android: `versionCode 22`, `versionName 1.0.22`.

## Causa raiz

O pré-detector visual é deliberadamente tolerante porque sua única função é decidir quando vale a pena acordar o OCR. Antes desta revisão, duas falhas de OCR logo após qualquer candidato visual eram interpretadas como falha de conclusão e produziam uma mensagem ao motorista. Cenas normais próximas ao destino podiam ter regiões escuras/douradas parecidas com o modal, produzindo falso candidato. A autoridade agora é o conteúdo semântico lido pelo OCR, não o pré-filtro visual.

## Escopo preservado

Nenhuma alteração em `GtoFastVisualDetector`, `GtoSelectionCoordinator`, reconhecimento/seleção de fretes, payload FIX18 ou `registerGtoTrip`.
