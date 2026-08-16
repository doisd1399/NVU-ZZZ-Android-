import re

with open("src/components/CompanyPerformanceCard.tsx", "r", encoding="utf-8") as f:
    content = f.read()

old_logic = """  const mediaDiaria = viagensRealizadas / runDays;

  const diasComViagem = new Set(currentTrips.map(t => new Date(t.metricDate).toDateString())).size;
  const constanciaScore = Math.min(100, Math.round((diasComViagem / runDays) * 100)) || 0;
  const expectedTrips = runDays * 2;
  const viagensScore = expectedTrips > 0 ? Math.min(100, Math.round((viagensRealizadas / expectedTrips) * 100)) : 0;
  const expectedEarnings = runDays * 200;
  const ganhosScore = expectedEarnings > 0 ? Math.min(100, Math.round((currentEarnings / expectedEarnings) * 100)) : 0;

  const scoreRaw = Math.round((constanciaScore + viagensScore + ganhosScore) / 3);
  const score = Math.round(scoreRaw);
  const displayScore = Math.min(100, score);"""

new_logic = """  const mediaDiaria = viagensRealizadas / runDays;

  const currentTripsAllCompanies = normalizedHistorico.filter(t => t.metricDate >= sDate && t.metricDate <= eDate);
  const companyStats: Record<string, { diasComViagem: Set<string>; viagens: number; ganhos: number }> = {};
  
  currentTripsAllCompanies.forEach(t => {
      const cId = t.empresaId || t.companyId;
      if (!cId) return;
      if (!companyStats[cId]) {
          companyStats[cId] = { diasComViagem: new Set(), viagens: 0, ganhos: 0 };
      }
      companyStats[cId].diasComViagem.add(new Date(t.metricDate).toDateString());
      companyStats[cId].viagens += 1;
      companyStats[cId].ganhos += t.normalizedValor;
  });

  if (!companyStats[companyId]) {
      companyStats[companyId] = { diasComViagem: new Set(), viagens: 0, ganhos: 0 };
  }

  let maxDias = 1;
  let maxViagens = 1;
  let maxGanhos = 1;

  Object.values(companyStats).forEach(st => {
      if (st.diasComViagem.size > maxDias) maxDias = st.diasComViagem.size;
      if (st.viagens > maxViagens) maxViagens = st.viagens;
      if (st.ganhos > maxGanhos) maxGanhos = st.ganhos;
  });

  const myStats = companyStats[companyId];
  const constanciaScore = Math.min(100, Math.round((myStats.diasComViagem.size / maxDias) * 100)) || 0;
  const viagensScore = Math.min(100, Math.round((myStats.viagens / maxViagens) * 100)) || 0;
  const ganhosScore = Math.min(100, Math.round((myStats.ganhos / maxGanhos) * 100)) || 0;

  const scoreRaw = Math.round((constanciaScore + viagensScore + ganhosScore) / 3);
  const score = Math.round(scoreRaw);
  const displayScore = Math.min(100, score);"""

if old_logic in content:
    content = content.replace(old_logic, new_logic)
    with open("src/components/CompanyPerformanceCard.tsx", "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched Company")
else:
    print("Old logic not found in Company")
