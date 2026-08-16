# NVU GTO R3.1 — Capacitor / Android v1.0.21

Status: fonte Android alinhada ao Dev Google AI Studio atual.
Data: 2026-08-11.

## Alteração final

Quando a operação não aceita novas viagens, o botão flutuante informa:

> Operação concluída. Inicie uma nova operação para continuar.

## Fluxo validado

- detector rápido de fretes preservado sem alteração;
- coordenador de seleção preservado sem alteração;
- troca temporária de aplicativo não apaga viagem ativa;
- restart do processo NVU recupera sessão ativa válida;
- retorno confirmado à lista de fretes durante uma rota invalida somente a viagem inacabada e prepara novo frete;
- resultado concluído não entra no caminho de cancelamento;
- tela de conclusão usa gate visual leve antes do OCR;
- OCR lento existe apenas como fallback;
- conclusão normal é automática;
- fallback manual só aparece como "Confirmar conclusão da entrega" quando necessário;
- ADS/bônus continua fora da conclusão normal;
- viagem concluída é selada e preservada em fila durável;
- falha de rede/Firebase não descarta entrega concluída;
- nova viagem é bloqueada enquanto a entrega anterior aguarda ACK;
- nova viagem é bloqueada após a operação terminar;
- erros de overlay/menu possuem diagnóstico;
- logout limpa sessão nativa e autenticação;
- Capacitor permanece em modo local, sem server.url remoto;
- nenhum keystore, local.properties ou node_modules é distribuído.

## Validações

- validate:gto-native: 47/47
- validate:gto-auto-sync: 74/74
- audit:gto-fix18: 26/26
- audit:gto-r2: 25/25
- audit:gto-r3: 25/25
- alinhamento cruzado AI Studio ↔ Capacitor: 48/48
- TypeScript/TSX: 147/147 arquivos parseados sem erro sintático
- scripts JS/MJS/CJS: 14/14 parseados
- bundle JavaScript existente: sintaxe validada
- versionCode 21 / versionName 1.0.21

## Geração do APK

O ambiente desta auditoria não possui todas as dependências npm em cache nem SDK Android suficiente para concluir um build Gradle completo. Portanto, antes de distribuir o APK oficial, faça obrigatoriamente:

npm install
npm run cap:sync:android

Depois abra o Android Studio e gere o release assinado com a chave oficial mantida fora deste ZIP.

O `cap:sync:android` é obrigatório porque ele recompila o frontend atual e o incorpora ao Android. Não gere o APK diretamente a partir de assets antigos sem essa sincronização.
