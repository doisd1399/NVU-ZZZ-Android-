# GTO Etapa 1 — FIX3 Captura de tela

Diagnóstico do teste anterior: o overlay recebia toques externos, porém a sessão MediaProjection não estava ativa (`captura 0x0`), portanto o OCR não recebia nenhum quadro e não havia fretes para associar ao toque.

Correções desta versão:
- remove temporariamente os overlays antes do consentimento de captura;
- em Android 14+ solicita captura da tela inteira para evitar seleção acidental da janela NVU;
- mantém a viagem em espera se a autorização falhar e oferece `Autorizar leitura da tela`;
- registra estados de captura e erro diretamente no menu de diagnóstico;
- restaura automaticamente o botão NVU ao voltar ao GTO;
- não altera Google AI Studio, Netlify, Firebase, ranking ou lançamento manual.

Teste esperado: após `Iniciar viagem` e autorizar a captura, o diagnóstico deve exibir `Captura: ACTIVE` e dimensões diferentes de `0x0`; somente então testar a lista de fretes.
