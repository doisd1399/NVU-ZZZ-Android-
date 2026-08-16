# GTO · FIX12 — Overlay estável e autorização sobre o simulador

## Causa raiz corrigida

1. O menu NVU ainda podia ser fechado por um callback assíncrono do detector de lista de fretes. O fechamento automático foi removido do caminho de OCR/detecção; agora o menu fecha apenas por ação do motorista, saída confirmada do GTO, parada do serviço ou abertura explícita de uma tela.
2. A detecção de primeiro plano destruía/recriava o overlay em qualquer pacote temporário (System UI, permission controller etc.). Foi adicionada tolerância temporal e filtragem da Activity auxiliar de permissão.
3. A Activity de autorização de MediaProjection podia compartilhar a task do NVU e relançava o GTO manualmente. Isso podia restaurar a MainActivity em modo freeform. A Activity agora usa task isolada, transparente, excluída dos recentes, landscape, sem animação e não abre MainActivity nem relança o GTO.

## Novo fluxo

- GTO em landscape → NVU → Iniciar viagem.
- O botão/menu NVU some somente durante o consentimento oficial do Android.
- O diálogo de MediaProjection aparece sobre o contexto horizontal do GTO.
- Após confirmar, a Activity auxiliar termina e o sistema retorna à task anterior, sem abrir o site NVU.
- A bolinha NVU é restaurada com tolerância a eventos transitórios do Android.
- O menu não pode mais ser fechado por OCR, detecção da lista ou mudança automática de estado.
- Ao detectar o frete, o menu é atualizado no mesmo overlay e a mensagem curta confirma que a viagem pode começar.
- A MediaProjection permanece ativa durante a viagem, mas o OCR fica inativo em TRIP_IN_PROGRESS até o motorista tocar em Finalizar viagem.
- Nenhuma permissão ou API de áudio é usada.

## Robustez adicional

- Debounce de 280 ms no toque da bolinha para eliminar abre/fecha duplo.
- Saída do GTO só remove overlay após 1,8 s de confirmação contínua fora do simulador.
- Fluxo de permissão possui grace period de 2,6 s.
- MediaProjection usa captura do display padrão.
- Android 14+ usa onCapturedContentResize para adaptar VirtualDisplay/ImageReader se o conteúdo mudar de tamanho/orientação.

## Validações executadas

- `npm run validate:gto-native`: 18/18 verificações aprovadas.
- `npm run verify:project`: aprovado.
- `npm run lint` / `tsc --noEmit`: aprovado.
- AndroidManifest.xml e styles.xml: XML válido.
- Parse sintático Java: sem erros de sintaxe detectados.

A compilação Gradle completa não foi executada neste ambiente porque o Gradle Wrapper precisa baixar a distribuição e o runtime está sem DNS externo. A compilação final continua sendo realizada no Android Studio.
