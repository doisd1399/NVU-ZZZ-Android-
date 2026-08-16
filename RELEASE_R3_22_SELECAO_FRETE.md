# NVU R3.22 — seleção de frete com painel aberto

## Identificação

- Release funcional: R3.22
- Web: 2.3.3
- Android: 1.0.39 (versionCode 39)
- Application ID: `com.nvu.operacional`

## Correção do teste real

Na R3.21, o sensor nativo recebia `ACTION_OUTSIDE`, mas descartava o evento se o
painel da bolinha NVU estivesse aberto. O GTO aceitava o frete, porém a linha não
era congelada e o estado permanecia em `WAITING_FREIGHT`.

A tela de direção apresentada logo depois também continha duas regiões laranja
no lado direito. Como tinham alturas muito diferentes, não pertenciam à lista de
fretes, mas o detector anterior aceitava essa combinação como “2 fretes”.

A R3.22 corrige os dois pontos:

1. o painel não bloqueia mais o toque feito fora dele na coluna `Aceitar`;
2. coordenadas válidas precisam cair em exatamente uma caixa `Aceitar` congelada;
3. a linha candidata ainda depende do fechamento real da lista;
4. coordenadas ocultadas pelo Android continuam usando o antes/depois visual;
5. pilhas com botões de alturas incompatíveis são rejeitadas como gameplay.

## Integridade preservada

Não foi reativada nenhuma escolha por proximidade, linha mais próxima ou simples
desaparecimento da lista. O snapshot imutável, o fingerprint SHA-256, o bloqueio
write-once do frete e a fila durável com remoção somente após ACK permanecem.

## Validação automatizada

- sintaxe dos 9 fontes Java Android: aprovada;
- fluxo nativo: 47/47;
- AutoSync/Firebase: 74/74;
- latch de recebimento: 32/32;
- estabilidade histórica: 63/63;
- modos e impressão: 20/20 + 12/12;
- integridade da seleção: 19/19;
- ponta a ponta: 34/34;
- paridade APK/Web: 36/36;
- barreira de captura R3.21: 13/13;
- regressão real da seleção R3.22: 8/8;
- TypeScript, build de produção e Firebase Functions: aprovados;
- fallback WebView: 62 arquivos idênticos ao `dist` por SHA-256.

## Publicação

Esta correção é nativa. Um deploy isolado no Netlify não corrige o APK R3.21; é
necessário gerar e instalar o APK/AAB 1.0.39 com a mesma chave de assinatura.
