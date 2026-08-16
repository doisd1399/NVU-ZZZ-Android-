# RELEASE GTO R3.13 — MODOS PRINT E AUTOMÁTICO

Versão Android: 1.0.33 (versionCode 33)

## Alteração exclusiva do GTO
- `Iniciar trabalho` abre um seletor com `Modo print` e `Modo automático`.
- `Modo automático` preserva integralmente o observador nativo, bolha, detecção de frete, conclusão e envio automático.
- `Modo print` abre `Lançar Viagem`.
- Origem e destino continuam manuais no GTO print.
- O comprovante é analisado localmente para preencher automaticamente o valor ganho.
- A análise também procura confirmação visual forte de anúncio assistido / valor dobrado.
- O botão normal `Dobrar valor / ADS` não é, sozinho, considerado prova de que o anúncio foi assistido.
- Quando há confirmação de anúncio/dobro, o lançamento normal é bloqueado, mantendo a mesma regra do modo automático.
- Se o print não puder ser analisado, o envio é bloqueado até o motorista selecionar uma imagem nítida.

## Compatibilidade
Os arquivos `src` compartilhados devem ser idênticos no Google AI Studio/Netlify e no pacote Capacitor/Android.
