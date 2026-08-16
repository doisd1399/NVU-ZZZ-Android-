# R3.31 — continuidade de viagem, pós-Receber e retorno ao GTO

Correções nativas Android sobre a base R3.30:

- durante `TRIP_IN_PROGRESS`, pixels parecidos com `Aceitar` são neutros e não podem gerar `FREIGHT_LIST_DURING_TRIP`;
- a leitura de lista durante uma viagem só é habilitada após `Trocar frete atual` ser armado explicitamente pelo motorista;
- o botão `Trocar frete atual` fica disponível durante a viagem sem depender de uma lista previamente detectada;
- fragmentos laranja no topo extremo da tela não podem formar uma lista de 1 frete;
- após `Receber` + ACK do backend, a NVU informa o envio automático e prepara uma nova sessão `WAITING_FREIGHT` quando a operação ainda estiver aberta;
- se a sincronização estiver pendente, a sessão concluída permanece preservada e nenhuma nova viagem é iniciada;
- sair rapidamente do GTO pausa a interpretação sem mudar o estado; ao voltar, somente evidências visuais transitórias são invalidadas e reconstruídas a partir de pixels atuais;
- retorno durante lista, viagem ou resultado não apaga frete selecionado, snapshot, Receive latch ou fila durável.

Versão Android: `1.0.48` / `versionCode 48`.
Web e Functions permanecem inalterados em relação à R3.30.
