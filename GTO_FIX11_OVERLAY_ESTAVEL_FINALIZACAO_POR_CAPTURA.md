# GTO · FIX11 — Overlay estável e finalização por captura sob demanda

## Causa raiz corrigida

O menu flutuante era removido por dois caminhos assíncronos:

1. `setTripState()` fechava o menu em qualquer transição de estado, inclusive `CONFIRMING_FREIGHT -> TRIP_IN_PROGRESS`.
2. Um quadro antigo da lista de fretes podia agendar `closeMenu()` e executar depois que o motorista já havia aceitado o frete e aberto o menu novamente.

Isso gerava o sintoma de abrir/piscar/fechar após aceitar um frete.

## Correção do overlay

- Mudanças de estado não removem mais a janela do menu.
- O conteúdo é atualizado dentro da mesma janela, sem desmontar/recriar o overlay.
- O auto-recolhimento da lista só pode fechar o menu enquanto o estado atual ainda for `WAITING_FREIGHT` e a lista estiver realmente recente.
- Quadros atrasados de MediaProjection não conseguem fechar o menu após `CONFIRMING_FREIGHT` ou `TRIP_IN_PROGRESS`.
- Após confirmação do frete é mostrado um aviso pequeno e temporário: `Frete identificado com sucesso · você pode iniciar a viagem.`

## Finalização por captura sob demanda

Durante `TRIP_IN_PROGRESS` o NVU não executa mais OCR contínuo da tela de resultado.

O menu oferece `Finalizar viagem`.

Fluxo:

1. O motorista conclui a entrega e deixa a tela `Concluído` visível.
2. Abre o botão NVU e toca `Finalizar viagem`.
3. O menu é recolhido.
4. A NVU usa apenas os próximos quadros da MediaProjection como screenshots em memória (máximo de 3 tentativas curtas).
5. O OCR procura `Concluído`, `Valor a receber` e o valor final.
6. Se identificado, o sistema informa o ganho e pede para usar `Receber` no GTO.
7. A observação volta a acontecer somente no curto período de validação pós-resultado para distinguir retorno normal ao jogo de anúncio/bônus.

Nenhuma imagem é gravada em arquivo ou enviada para a nuvem. Os frames são processados localmente em memória e descartados.

## Falha segura

Se a tela `Concluído` não estiver visível ou não puder ser reconhecida após as tentativas rápidas, a viagem não é finalizada por aproximação. O NVU mantém a viagem em andamento e informa para abrir a tela de conclusão e tentar novamente.
