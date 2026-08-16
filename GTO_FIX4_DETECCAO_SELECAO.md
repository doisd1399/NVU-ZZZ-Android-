# GTO Etapa 1 — FIX4 Detecção da seleção do frete

Correção nativa/Capacitor. Não exige alteração no projeto web do Google AI Studio/Netlify.

## Problema confirmado no teste
- MediaProjection ativa e resolução correta.
- OCR reconhece 3 a 5 fretes.
- ACTION_OUTSIDE chega ao overlay.
- Após tocar em Aceitar, o GTO fecha a lista, mas a viagem permanecia em WAITING_FREIGHT.

## Mudança
- O toque passa a gerar apenas um candidato de frete enquanto a lista foi vista recentemente.
- A seleção só é confirmada quando o OCR detecta a saída real da tela de fretes.
- O mapeamento tenta coordenadas raw/alternativas e vários mapeamentos do eixo Y para acomodar diferenças do ACTION_OUTSIDE em overlays.
- O diagnóstico mostra a última coordenada recebida e a origem do candidato.
- Após confirmação, o menu mostra "Frete detectado" com km/valor/destino disponíveis.

## Teste esperado
1. NVU > Iniciar viagem.
2. Abrir lista de fretes e aguardar OCR mostrar FREIGHT_LIST.
3. Fechar o menu e tocar Aceitar em um frete.
4. Após o GTO sair da lista, abrir NVU.
5. Esperado: "Viagem em andamento" e "Frete detectado: ...".
