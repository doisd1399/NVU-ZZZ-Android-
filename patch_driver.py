import re

with open("src/components/DriverPerformanceCard.tsx", "r", encoding="utf-8") as f:
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

  // -- RELATIVE PERFORMANCE CALCULATION --
  let currentTripsAllDrivers = normalizedHistorico.filter(t => t.metricDate >= sDate && t.metricDate <= eDate);
  
  if (activeCompanyId && allCompanyMembers) {
     const memberIds = new Set(allCompanyMembers.map(m => m.id));
     currentTripsAllDrivers = currentTripsAllDrivers.filter(t => memberIds.has(t.driverId));
  }

  const driverStats: Record<string, { diasComViagem: Set<string>; viagens: number; ganhos: number }> = {};
  
  currentTripsAllDrivers.forEach(t => {
      const dId = t.driverId;
      if (!dId) return;
      if (!driverStats[dId]) {
          driverStats[dId] = { diasComViagem: new Set(), viagens: 0, ganhos: 0 };
      }
      driverStats[dId].diasComViagem.add(new Date(t.metricDate).toDateString());
      driverStats[dId].viagens += 1;
      driverStats[dId].ganhos += t.normalizedValor;
  });

  if (!driverStats[driverId]) {
      driverStats[driverId] = { diasComViagem: new Set(), viagens: 0, ganhos: 0 };
  }

  let maxDias = 1;
  let maxViagens = 1;
  let maxGanhos = 1;

  Object.values(driverStats).forEach(st => {
      if (st.diasComViagem.size > maxDias) maxDias = st.diasComViagem.size;
      if (st.viagens > maxViagens) maxViagens = st.viagens;
      if (st.ganhos > maxGanhos) maxGanhos = st.ganhos;
  });

  const myStats = driverStats[driverId];
  const constanciaScore = Math.min(100, Math.round((myStats.diasComViagem.size / maxDias) * 100)) || 0;
  const viagensScore = Math.min(100, Math.round((myStats.viagens / maxViagens) * 100)) || 0;
  const ganhosScore = Math.min(100, Math.round((myStats.ganhos / maxGanhos) * 100)) || 0;

  const scoreRaw = Math.round((constanciaScore + viagensScore + ganhosScore) / 3);
  const score = Math.round(scoreRaw);
  const displayScore = Math.min(100, score);
  // --------------------------------------"""

if old_logic in content:
    content = content.replace(old_logic, new_logic)
    with open("src/components/DriverPerformanceCard.tsx", "w", encoding="utf-8") as f:
        f.write(content)
    print("Patched Driver")
else:
    print("Old logic not found in Driver")
