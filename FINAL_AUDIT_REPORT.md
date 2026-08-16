# Relatório de Auditoria e Correção Definitiva

## 1) MIGRAÇÃO DEFINITIVA DE FOTOS
- **Auth**: Fotos advindas de autenticação (ex: Google) agora ficam exclusivamente na propriedade `authPhotoURL` e não vazam para componentes de tela se o usuário preferiu não enviar imagem.
- **Perfil do Motorista**: Atualizados os layouts (`DriverLayout`, `AdminLayout`, `SelectProfile`, `Profile`, `OperationsTab`) para lerem estritamente `profilePhotoURL`, eliminando correntes de fallback com `avatar` e `photoURL`.
- **Empresas**: Componente `RegisterCompany` agora escreve apenas `companyLogoURL`. Não herda imagem do usuário se a mesma for deixada em branco.
- **Inscrição de Motoristas**: Formulários de recrutamento utilizam `applicationPhotoURL`, sem sujar a base ou puxar foto do Google Auth como salvaguarda indesejada.

## 2) CORREÇÃO DO CADASTRO E INSCRIÇÃO
- Eliminado o uso da chave `photoURL` nos estados locais de `RegisterCompany` e `RecruitmentApply`. Quando não há envio de imagem, os documentos são salvos com o valor puro sem preenchimento automático.
- Ajustes refletidos imediatamente no `AppContext`, sem necessidade de re-autenticação.
- Atualizado o sistema de legados e migrações no `RecruitmentTab` para suportar o espelhamento em `applicationPhotoURL`.

## 3) STATUS DE INSCRIÇÕES PADRONIZADO
- Os valores de negócio em toda a arquitetura que continham nomenclaturas em PT-BR (`"pendente"`, `"aprovado"`, `"recusado"`, `"cancelado"`) foram migrados de forma definitiva e segura para o padrão da aplicação: `"pending"`, `"approved"`, `"rejected"`, `"cancelled"`.
- As lógicas de triagem de `RecruitmentTab`, `JoinCompany`, e o escopo de notificações (`notificationScope`) acompanharam esta normatização, corrigindo quebras silenciosas.

## 4) RESPOSTAS E NOTIFICAÇÕES
- O bloco funcional do `AppContext` foi estendido. Agora, os métodos `approveRecruitmentApplication`, `rejectRecruitmentApplication` e seus análogos disparam eventos de notificação precisos, garantindo que a promessa "Solicitação aprovada" ou "Solicitação recusada" seja cumprida pontualmente para o motorista alvo.

## 5) BUILD E CONSOLIDAÇÃO
- Verificação executada varrendo os resquícios textuais de `photoURL` e `avatar`.
- O build da interface compila perfeitamente após a resolução de duplicações assíncronas, assegurando a integridade do ecossistema.
