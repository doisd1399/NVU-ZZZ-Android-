# GTO FIX9 — seleção visual por buffer, sem Acessibilidade

## Motivo
O FIX8 introduziu AccessibilityService/TouchInteractionController para tentar obter coordenadas reais do toque. No aparelho de teste isso não resolveu a seleção e ainda interferiu na interação/overlay, além de aumentar o bloqueio pelo Play Protect.

## Arquitetura FIX9
- Remove completamente o AccessibilityService do Manifest.
- Mantém MediaProjection + botão overlay + acesso de uso.
- Detecta os botões laranja `Aceitar` estruturalmente, independente do OCR.
- Enquanto a lista está aberta mantém um pequeno histórico de assinaturas visuais dos botões.
- `ACTION_OUTSIDE` é usado apenas como marcador de tempo; as coordenadas 0,0 são ignoradas.
- Após qualquer toque na lista, captura os frames imediatamente seguintes em alta frequência.
- Só confirma uma linha quando um único botão `Aceitar` muda e os demais permanecem estáveis.
- Toques nas setas de página/back não são considerados seleção porque não produzem mudança exclusiva em um botão `Aceitar`.
- Antes do toque congela a imagem da página. Depois que a linha é confirmada, o OCR é executado somente naquele cartão congelado; assim a tela pode desaparecer sem perder os dados.
- O OCR dedicado usa o confidence score do ML Kit e funde o resultado com o consenso já obtido daquela página.
- O menu grande continua sendo recolhido automaticamente na lista; durante a escolha fica somente a bolha NVU.

## Segurança de dados
Se a linha não puder ser distinguida visualmente ou se Carga/Empresa/Destino/Km/Ganhos não forem válidos, o sistema não associa outro frete por aproximação.
