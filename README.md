# NVU — Capacitor Android / GTO

Projeto Android/Capacitor alinhado à mesma camada web do projeto Google AI Studio/Netlify.

O APK usa o Netlify como runtime remoto (`capacitor.remote.json`). Assim, novo deploy do Netlify atualiza automaticamente a interface e lógica web do APK, sem recompilar o APK para cada alteração web.

A camada nativa Android permanece responsável pelo Observador GTO, botão flutuante, permissões, captura de tela, integração nativa e demais APIs Capacitor.
