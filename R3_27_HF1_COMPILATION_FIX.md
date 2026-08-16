# NVU R3.27-HF1 — correção de compilação Android

## Causa raiz

`GtoObserverPlugin.openGto()` passou a consultar `prefs` na R3.27 para retornar o estado preparado, mas a variável `SharedPreferences prefs` não existia no escopo desse método. O verificador anterior fazia apenas parse sintático, por isso não detectava o símbolo ausente.

## Correções

- `openGto()` agora cria `Context context` e `SharedPreferences prefs` no próprio escopo antes de qualquer uso.
- O start do GTO usa o mesmo `context` local.
- A suíte R3.27 ganhou regressão específica para o escopo de `SharedPreferences` em `openGto()`.
- `PREPARAR-ANDROID-WINDOWS.bat` agora exige JDK 21 e executa `:app:compileDebugJavaWithJavac` antes de declarar o projeto preparado, impedindo publicação de uma release com erro Java de símbolos.

## Validação executada neste ambiente

- JavaSyntaxCheck: PASS (15 fontes)
- validate-gto-native-flow: 49/49 PASS
- validate-gto-auto-sync: 74/74 PASS
- R3.26 deterministic-flow: 21/21 PASS
- R3.25 detection-flow: 16/16 PASS
- R3.27 runtime-flow: 31/31 PASS

A compilação Gradle Android completa não foi executada neste ambiente por ausência do Android SDK 36. O pacote força essa compilação real no Windows durante `PREPARAR-ANDROID-WINDOWS.bat` antes de permitir seguir para publicação.

Versões funcionais permanecem: R3.27 / Web 2.3.8 / Android 1.0.44 / versionCode 44.
