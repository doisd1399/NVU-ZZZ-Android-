# NVU v7.0 — Fonte canônica do ranking do motorista

Correção aplicada:
- Ranking Interno e Global passam a usar os mesmos documentos canônicos de viagem.
- O ranking interno combina o stream global filtrado pela empresa com o listener da empresa como fallback.
- Registros legados com `companyId` ou `company_id` deixam de ser omitidos pelo listener que consulta `empresaId`.
- Perfil comum e perfil isolado do Painel Sênior usam a mesma função de composição.

Teste de regressão:
- Mesmo próximo colocado nos dois rankings => mesmo ganho e mesma diferença financeira, mesmo com posições diferentes.
- Próximos colocados diferentes => diferenças financeiras independentes.
