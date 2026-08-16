# R3.27-HF2 — correção de detecção do Java 21 no Windows

Este hotfix não altera a lógica GTO, Web, Firebase ou a versão Android.

Corrige `PREPARAR-ANDROID-WINDOWS.bat` e `GERAR-APK-DEBUG-WINDOWS.bat`, que executavam o JBR do Android Studio dentro de `for /f` usando um caminho com espaços (`C:\Program Files\...`). Em determinados CMDs, isso era interpretado como `C:\Program`, interrompendo a preparação apesar de o Java 21 estar instalado.

A detecção agora:

1. testa diretamente o JBR do Android Studio por arquivo temporário;
2. tenta `JAVA_HOME` se já estiver configurado;
3. tenta Java 21 no `PATH`;
4. só então bloqueia a preparação.

A mesma correção foi aplicada ao gerador de APK para manter preparação e build consistentes.
