# Auditoria final de fotos de motoristas

Causa raiz:
Os componentes fora do Ranking acessavam diretamente profilePhotoURL, sem fallback para campos legados.

Correção:
Criado resolveDriverPhoto() como fonte única de resolução de avatar.

Mantido:
- simulatorId
- ranking
- histórico
- lógica operacional

Componentes ajustados:
- DriverProfileIsolated
- DriversTab
- AssignJob
- OperationsTab
- Profile
- DriverLayout
