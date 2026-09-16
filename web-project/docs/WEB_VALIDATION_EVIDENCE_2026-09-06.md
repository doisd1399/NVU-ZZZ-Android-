# Validação Web real — evidências

## Domínio público

Em 2026-09-06, o domínio `https://stirring-pavlova-ca6808.netlify.app/` carregou a tela inicial NVU no navegador sandbox. A tela exibiu `NVU`, `Gestão Operacional de Logística`, `Acesse sua conta`, o botão `Fazer Login` e a opção `Quero me inscrever`.

A autenticação ainda não foi executada nesta fase. O teste local anterior foi interrompido porque seu link temporário não era o domínio público; a validação atual está sendo feita no Netlify público correto.
## Build e manifesto públicos

O endpoint público `nvu-build.json` respondeu com Web `2.3.51`, runtime `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR`, `capacitorRuntime: local`, `otaEnabled: true` e manifesto `production-286`.

O manifesto público respondeu com `bundleId: production-286-2.3.51`, `webVersion: 2.3.51`, `nativeVersionCode: 286`, `nativeChannel: production-286`, URL de download same-origin HTTPS, checksum `de3dd033227b02ceef2ee6051410e58859b076b68bef5d552317b2ddcf27763e` e `signatureRequired: true`.

Esta confirmação é apenas de conteúdo público; ainda não comprova login, Profile Index ou navegação em sessão autenticada.
## Falha observada no login público

Na rota pública `/login`, após a tentativa de autenticação, a aplicação exibiu: **“O seletor Google não foi exibido. Verifique se o domínio está autorizado e tente novamente.”**

A inspeção da página confirmou `window.google === true`, `window.google.accounts.id === true` e carregamento de `https://accounts.google.com/gsi/client`. Também foram observadas conexões Firestore normais para o projeto `vtc-frota-log`.

Portanto, a evidência atual não indica que o script GIS esteja ausente. Ainda não há prova suficiente para atribuir a causa exclusivamente ao domínio autorizado; é necessário capturar o retorno da tentativa GIS/configuração do cliente e comparar a origem pública `https://stirring-pavlova-ca6808.netlify.app` com as origens OAuth autorizadas.
## Auditoria do bundle público de autenticação

O bundle público compilou o client ID Google `451561168694-9nldqgb2edr91j2flrl0m7305dd6gbj5.apps.googleusercontent.com`, o `authDomain` `vtc-frota-log.firebaseapp.com` e o carregamento de `https://accounts.google.com/gsi/client`.

A função pública de login chama `google.accounts.id.prompt()` e, quando `isNotDisplayed()` ou `isSkippedMoment()` retorna verdadeiro, converte o motivo específico em `GOOGLE_IDENTITY_NOT_DISPLAYED`; a UI então mostra a mensagem genérica sobre domínio autorizado. Assim, o texto exibido não prova sozinho que o domínio está ausente das origens autorizadas. O motivo detalhado (`getNotDisplayedReason()`/`getSkippedReason()`) ainda precisa ser capturado em uma tentativa diagnóstica controlada.
## Causa funcional confirmada do login sem seletor

A reprodução pública e a leitura do código confirmam que o único caminho Web é `google.accounts.id.prompt()`. O bundle chama `prompt`, recebe `isSkippedMoment()` com `skippedReason: "unknown_reason"`, converte isso em `GOOGLE_IDENTITY_NOT_DISPLAYED` e encerra a tentativa. O componente `Login` apenas mostra a mensagem e não possui um fallback `signInWithPopup` ou outro seletor OAuth explícito.

A causa funcional da falha de acesso é, portanto, **dependência exclusiva do One Tap/prompt para iniciar o login**. O motivo externo específico para o GIS não exibir o prompt nesta origem/navegador ainda não é determinado; a mensagem atual atribui genericamente o problema ao domínio, mas o retorno capturado foi `unknown_reason`. A correção mínima possível é adicionar fallback Web de popup somente quando o prompt não for exibido, mantendo o caminho nativo Android separado. Isso ainda exigiria validação e eventual configuração de origem autorizada antes de publicar.
