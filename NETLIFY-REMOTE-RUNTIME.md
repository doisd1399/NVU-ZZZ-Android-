# NVU — Runtime Web remoto no APK

## Objetivo

O APK mantém a camada nativa Android embarcada, mas o WebView de produção carrega o frontend pelo Netlify.

- **Netlify:** React/TSX, CSS, telas, ranking, relatórios, histórico, Firebase/web logic e demais recursos web.
- **APK nativo:** GTO Observer, MediaProjection, OCR/detecção nativa, overlay, Foreground Service, permissões e plugins Android.

## Configuração atual

`capacitor.remote.json` está habilitado para:

`https://stirring-pavlova-ca6808.netlify.app`

O arquivo `capacitor.config.ts` lê essa configuração e define `server.url` somente quando o modo remoto está habilitado.

## Importante

O comando `cap:sync:android` não deve mais desativar o modo remoto. O modo local foi separado para:

`npm run cap:sync:android:local`

Para gerar um APK que consuma o Netlify:

`npm run cap:sync:android:remote`

Depois que esse APK estiver instalado, novos deploys web no Netlify são carregados pelo WebView sem reconstruir o APK, desde que a alteração não exija código nativo.

## Atualização em execução

`src/lib/deployRecovery.ts` verifica `nvu-build.json` sem cache e recarrega o WebView quando detecta um `buildId` diferente. O `netlify.toml` mantém `index.html` e `nvu-build.json` sem cache e assets versionados com cache imutável.

## Camada nativa

A release R3.26 mantém as barreiras de integridade da R3.25 e adiciona uma política
determinística para separar estado da viagem, tela detectada, foreground do GTO,
MediaProjection e sincronização. A leitura é pausada fora do GTO sem limpar a viagem,
telas desconhecidas são neutras, `CONFIRMING_FREIGHT` não pode ser reiniciado por uma
lista ainda visível e uma lista reaberta durante a rota não substitui o frete atual sem
ação explícita do motorista.

Essas mudanças exigem o novo APK/AAB 1.0.43 (versionCode 43). A interface Web também
foi atualizada para exibir os novos diagnósticos e estados; por isso o `dist` R3.26 deve
ser publicado no Netlify para manter a camada remota alinhada ao APK. Futuras mudanças
exclusivamente Web continuam podendo ser entregues pelo Netlify sem recompilar o aplicativo.
