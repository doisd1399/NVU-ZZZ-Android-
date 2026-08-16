# Auditoria final simuladores NVU v8

Correções aplicadas:
- simulatorId tornou-se chave lógica principal.
- Criadas funções resolveSimulatorId e resolveSimulatorName.
- RecruitmentApply corrigido para usar selectedSimulatorId.
- RankingGlobal e metricsEngine ajustados para comparar por ID.
- Campos simulatorName e simulator preservados para compatibilidade.

Pendências não destrutivas:
- Testes reais Firebase/APK devem ser executados no ambiente Dev.
