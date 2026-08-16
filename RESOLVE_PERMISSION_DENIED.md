# Resolução do Erro de Permissão nos Simuladores

## O Problema
O aplicativo estava recebendo o erro `Missing or insufficient permissions` ao tentar buscar os simuladores.
Isso ocorre porque, embora tenhamos atualizado o arquivo `firestore.rules` localmente para permitir a leitura pública de simuladores (`allow read: if true;`), **estas novas regras ainda não foram publicadas (deployed) no seu projeto Firebase real**.
Como o aplicativo tentava buscar os dados de forma anônima (sem o usuário estar logado) nas telas públicas e o Firebase bloqueava a requisição, ele caía no bloco de erro, que não inicializava os simuladores e travava o formulário.

## A Solução Temporária / Fallback Aplicado
No arquivo `src/context/AppContext.tsx`, atualizei o tratamento de erro da assinatura da coleção `simulators`. 
Agora, se o Firebase rejeitar a conexão (por erro de permissão ou qualquer outro motivo), a aplicação vai silenciar a falha no console e preencher automaticamente o array `simulators` com os valores padrão (`"GTO", "WTDS", "WBDS", "TOE 3", "ETS 2", "ATS", "PBS"`). 
Isso permite que os formulários de Cadastro de Empresa e Inscrição de Motorista funcionem perfeitamente de forma pública, mesmo enquanto suas regras do Firestore não estão atualizadas.

## O Que Você Precisa Fazer
Para que o sistema passe a ler os simuladores diretamente do banco de dados para usuários deslogados, você precisará **fazer o deploy das regras do Firestore** (Firebase CLI):

1. Se você tiver a Firebase CLI instalada na sua máquina local, rode:
   `firebase deploy --only firestore:rules`
2. Ou, alternativamente, acesse o **Console do Firebase**, vá até **Firestore Database** -> aba **Regras (Rules)**, copie todo o conteúdo do arquivo local `firestore.rules` do nosso repositório e cole lá, clicando em "Publicar".
