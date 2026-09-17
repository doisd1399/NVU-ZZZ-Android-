# Runbook de release Web, APK e OTA

## Fonte única

O Web e o APK devem ser gerados a partir do mesmo commit. O build Web de produção usa a versão `1.31.0`, o runtime `R3.34-PC-WEB-AUTH-ARCHITECTURE-MODULAR` e o canal nativo `production-1310`. Nunca publique um `dist` gerado de outra cópia do projeto.

## Correção monetária Senior

A edição de viagem no Histórico usa a sessão Senior validada pelo Firebase como fonte principal, mantendo `sessionStorage.seniorAccess` somente como compatibilidade. Toda edição grava `valor` em reais, `valorCents` em centavos, `valorEditadoNoPainelSenior`, `valorEditadoPor`, `valorEditadoPorNome` e `valorEditadoEm`.

## Manifesto OTA

O manifesto precisa conter `artifactType: zip`, `bundleId: production-1310-<webVersion>`, `webVersion`, `runtimeRevision`, `nativeChannel: production-1310`, `nativeVersionCode` igual ao `versionCode` do APK, `downloadUrl` HTTPS na mesma origem do manifesto, checksum SHA-256 do ZIP e `signature` RSA válida.

## Chaves

A chave privada RSA usada para assinar a OTA deve existir somente como Secret no pipeline de release, por exemplo `OTA_PRIVATE_KEY_PEM` ou equivalente. A chave pública correspondente deve estar embutida no APK e versionada como material público. Nunca colocar a chave privada no repositório, no ZIP Web ou no APK. Ao trocar a chave, gerar um novo APK com a nova chave pública antes de publicar o manifesto.

## Ordem obrigatória

1. Executar lint e testes, incluindo `validate-senior-navigation`.
2. Gerar o `dist` com `NVU_RUNTIME_REVISION` e `NVU_NATIVE_CHANNEL` definidos.
3. Publicar o Web e validar `nvu-build.json`.
4. Criar o ZIP OTA a partir do mesmo `dist`.
5. Calcular SHA-256, assinar o checksum/manifesto com a chave privada OTA e publicar o ZIP e manifesto no canal correto.
6. Gerar o APK assinado usando o mesmo commit e validar `applicationId`, `versionName`, `versionCode`, assinatura V2/V3 e o Web embutido.
7. Verificar que o manifesto aceita o `nativeVersionCode` do APK e que `downloadUrl` e manifesto respondem HTTP 200.

Sem a chave privada OTA correspondente à chave pública embarcada, não publicar um manifesto alegando assinatura válida. Nesse caso, o APK pode ser entregue como release nativa, mas a OTA deve permanecer bloqueada até o Secret ser configurado.
