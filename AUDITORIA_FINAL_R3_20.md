# NVU R3.20 — Auditoria de Compatibilidade Web / Netlify / Capacitor / Android
Data: 2026-08-13

## Resultado objetivo

VERSÃO WEB ENCONTRADA
- Projeto Google AI Studio/Netlify fornecido: 2.3.0.
- A URL configurada do runtime é https://stirring-pavlova-ca6808.netlify.app.
- A versão publicada ao vivo no Netlify não pôde ser consultada diretamente neste ambiente por indisponibilidade de resolução/rede externa; portanto, 2.3.0 é a versão comprovada no projeto Web fornecido, não uma afirmação de leitura do deploy ao vivo.

VERSÃO NO APK
- Camada nativa original: Android versionCode 36 / versionName 1.0.36.
- Projeto corrigido: versionCode 37 / versionName 1.0.37.
- Versão Web da camada compartilhada corrigida: 2.3.1 / R3.20.

## Arquitetura real encontrada

O APK NÃO está usando a Web embutida como runtime de produção.

Evidências:
- capacitor.config.ts define `webDir: "dist"` apenas como diretório de build/empacotamento, mas também define `server` carregado de `capacitor.remote.json`.
- capacitor.remote.json possui `enabled: true`.
- `server.url` é HTTPS e aponta para `https://stirring-pavlova-ca6808.netlify.app`.
- android/app/src/main/assets/capacitor.config.json contém a mesma `server.url`.
- Portanto, o WebView de produção carrega o frontend remotamente do Netlify.
- A camada Android nativa continua embarcada no APK: GTO Observer, MediaProjection, OCR/detecção, overlay, Foreground Service, permissões e plugins Capacitor.

Consequência: um novo deploy Web no Netlify altera a UI/lógica Web usada pelo APK sem recompilar o APK, desde que a mudança não exija código nativo.

## Divergências encontradas

1. Web fornecido estava em 2.3.0, enquanto a fonte Capacitor estava em 2.3.1.
2. `functions/src/gtoTrips.ts` no Web não continha as validações R3.19/R3.20 de identidade do frete, `selectedRow`, `freightFingerprint` e campos de origem/destino ampliados.
3. O Web não exportava `syncGtoTripState`.
4. O Web não possuía a fonte canônica Firestore `gto_active_gto_sessions`.
5. O Web não possuía o hook/serviço de leitura do estado canônico.
6. Dashboard/GTO Observer do Web não estava alinhado com o estado canônico.
7. Firestore rules do Web não continham as coleções canônicas GTO.
8. O APK nativo estava em 1.0.36.
9. Havia scripts de auditoria presos a versões antigas 1.0.34/1.0.35/1.0.36, inclusive um script chamado pelo fluxo de sincronização.
10. Os assets Web dentro de `android/app/src/main/assets/public` são antigos em relação à fonte Web atual. Isso não altera o runtime de produção porque `server.url` remoto está habilitado, mas é uma divergência real de artefato e deve ser regenerada antes de uma release física.

## Causa raiz

A causa principal foi sincronização parcial entre o Dev do Google AI Studio e a árvore Capacitor:
- o Web continuou em uma revisão anterior;
- a camada Android recebeu correções R3.17/R3.19/R3.20 antes que o backend/Web fossem consolidados na mesma revisão;
- o APK foi configurado corretamente para runtime remoto, mas a fonte Web apontada pelo Netlify ainda não continha todas as alterações exigidas pela camada nativa;
- os scripts de release também ficaram com expectativas de versões antigas.

## Correções aplicadas

- Web e Capacitor alinhados na versão funcional R3.20.
- Web atualizado para 2.3.1.
- Android atualizado para 1.0.37 / versionCode 37.
- `functions/src/gtoTrips.ts` alinhado com o contrato nativo R3.19/R3.20.
- `functions/src/gtoState.ts` incluído/exportado como fonte server-side do estado GTO.
- `src/services/gtoCanonicalState.ts` e `src/hooks/useGtoCanonicalState.ts` incluídos.
- `GtoObserverSetup` e Dashboard alinhados ao estado canônico.
- Firestore rules atualizadas para leitura somente do próprio estado canônico.
- Diagnóstico Web existente preservado.
- Configuração Capacitor remota preservada e verificada.
- Scripts de auditoria com versões antigas corrigidos para 1.0.37.
- Firebase permanece no projeto `vtc-frota-log`, região Functions `us-central1`.
- Plugins Capacitor e permissões nativas foram preservados.

## Testes executados

- APK × Web paridade R3.20: 36/36 PASS.
- Fluxo nativo GTO: 47/47 PASS.
- Sincronização automática/Firebase: 74/74 PASS.
- Integridade ponta a ponta R3.19: 34/34 PASS.
- Seleção/fingerprint R3.17: 19/19 PASS.
- Validação Web/Firebase GTO: 47/47 PASS.
- Modos GTO/print: 20/20 PASS.
- Runtime remoto Capacitor: PASS.
- Máquina de estados: todos os cenários simulados PASS.

## Fluxo final validado estaticamente

Login → navegação → Firebase/Auth → operação → GTO → detecção → seleção fail-closed → snapshot imutável → viagem → conclusão → Receber → payload selado → fila durável → registerGtoTrip → histórico/progresso → sincronização/ACK → estado canônico Firestore → Web.

## Limitações deste ambiente

Não foi possível:
- acessar o deploy vivo do Netlify;
- executar `npm run build`, porque as dependências Node não estão instaladas e o ambiente não possui acesso externo para instalá-las;
- executar `npx cap sync android` pelo mesmo motivo;
- executar o Gradle release, pois a distribuição Gradle 8.14.3 não está disponível localmente e o ambiente não consegue baixá-la;
- gerar um APK release físico neste ambiente.

Assim, não é declarado um APK release compilado. Os projetos-fonte corrigidos estão prontos para o ciclo final:

npm ci
npm run cap:sync:android:remote
cd android
./gradlew assembleRelease

## Resultado

WEB/NETLIFY + CAPACITOR + ANDROID:
- arquitetura: COMPATÍVEL, com runtime Web remoto;
- lógica Web/backend/nativa: ALINHADA na revisão corrigida R3.20;
- estado GTO: fonte canônica compartilhada em Firebase;
- autenticação/Firebase: mesmo projeto;
- APIs: contrato nativo/backend alinhado;
- permissões/plugins: preservados;
- código antigo no runtime de produção: bloqueado pela configuração remota;
- artefato Web embutido: ainda precisa ser regenerado pelo `npm run build && npx cap sync android` antes de uma release física, embora não seja o runtime de produção atual.
