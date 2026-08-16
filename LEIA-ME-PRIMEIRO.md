# NVU R3.34-PC-HF10

Base funcional: **R3.34 aprovada**. A R3.35 rejeitada não é usada como base.

## Correção crítica HF10

A falha restante estava no pós-consentimento do `MediaProjection`: após o usuário aceitar **Compartilhar tela**, o fluxo ainda dependia de um `GtoObserverService` já vivo e deixava o token vinculado sem criar imediatamente a sessão real de captura. Em Android/OEMs mais agressivos isso podia terminar em `onStop()` antes da primeira `VirtualDisplay`, deixando a bolha mostrar autorização novamente.

A HF10 muda para a sequência:

`RESULT_OK -> foreground service mediaProjection -> getMediaProjection -> registerCallback -> createVirtualDisplay -> primeiro frame`

A `VirtualDisplay` é criada **imediatamente** usando a geometria horizontal já verificada pelo host transparente. A detecção, porém, só processa frames quando o GTO volta a ser confirmado em primeiro plano.

Android: **1.0.62 / versionCode 62**.

A prova de leitura funcional agora é o **primeiro frame efetivamente recebido**, não apenas o clique em Compartilhar tela.

## Fluxo protegido

- bolha NVU antes da autorização;
- autorização somente sobre GTO horizontal;
- sem passagem pela MainActivity;
- resultado enviado ao serviço por `ACTION_START_PROJECTION`;
- funciona mesmo se o processo/serviço precisar ser recriado durante o diálogo;
- uma autorização = uma `VirtualDisplay`;
- resize usa `VirtualDisplay.resize()` + `setSurface()`;
- nova autorização somente após encerramento terminal real.

As validações de frete, seleção, resultado, Receber e envio automático continuam preservadas.

Se Netlify/Firebase Functions já estão na HF2 ou posterior, a HF10 **não exige novo deploy**: `src` e `functions` não foram alterados.

No Windows, execute `PREPARAR-ANDROID-WINDOWS.bat` e depois siga `COMANDOS-R3.34-HF10-RELEASE-WINDOWS.txt`.
