import re

def fix_company():
    filepath = "src/components/CompanyPerformanceCard.tsx"
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Update ranking logic
    old_ranking = """  const currentPeriodStatsMap: Record<string, { trips: number; val: number }> =
    {};
  normalizedHistorico.forEach((trip) => {
    if (trip.metricDate >= sDate && trip.metricDate <= eDate) {
      
      const mId = trip.empresaId || trip.companyId;
      if (!mId) return;
      if (!currentPeriodStatsMap[mId])
        currentPeriodStatsMap[mId] = { trips: 0, val: 0 };
      currentPeriodStatsMap[mId].trips += 1;
      currentPeriodStatsMap[mId].val += trip.normalizedValor;
    }
  });

  const currentPeriodStatsArray = Object.keys(currentPeriodStatsMap)
    .map((id) => ({
      id,
      ...currentPeriodStatsMap[id],
    }))
    .sort((a, b) => {
      if (b.val !== a.val) return b.val - a.val;
      return b.trips - a.trips;
    });

  const currentCompanyPos = currentPeriodStatsArray.findIndex(
    (d) => d.id === companyId,
  );
  const currentPosition = currentCompanyPos >= 0 ? currentCompanyPos + 1 : "--";
  const currentTotalDrivers =
    allCompanies && allCompanies.length > 0
      ? allCompanies.length
      : Math.max(currentPeriodStatsArray.length, 1);
  const currentDiffToNext =
    currentCompanyPos > 0
      ? currentPeriodStatsArray[currentCompanyPos - 1].val -
        (currentCompanyPos >= 0
          ? currentPeriodStatsArray[currentCompanyPos].val
          : 0)
      : 0;"""

    new_ranking = """  const currentPeriodStatsMap: Record<string, { trips: number; val: number }> = {};
  
  if (allCompanies && allCompanies.length > 0) {
    allCompanies.forEach(c => {
      currentPeriodStatsMap[c.id] = { trips: 0, val: 0 };
    });
  }

  normalizedHistorico.forEach((trip) => {
    if (trip.metricDate >= sDate && trip.metricDate <= eDate) {
      const mId = trip.empresaId || trip.companyId;
      if (!mId) return;
      if (!currentPeriodStatsMap[mId])
        currentPeriodStatsMap[mId] = { trips: 0, val: 0 };
      currentPeriodStatsMap[mId].trips += 1;
      currentPeriodStatsMap[mId].val += trip.normalizedValor;
    }
  });

  // Ensure current company is in the map
  if (!currentPeriodStatsMap[companyId]) {
    currentPeriodStatsMap[companyId] = { trips: 0, val: 0 };
  }

  const currentPeriodStatsArray = Object.keys(currentPeriodStatsMap)
    .map((id) => ({
      id,
      ...currentPeriodStatsMap[id],
    }))
    .sort((a, b) => {
      if (b.val !== a.val) return b.val - a.val;
      return b.trips - a.trips;
    });

  const currentCompanyPos = currentPeriodStatsArray.findIndex(
    (d) => d.id === companyId,
  );
  const currentPosition = currentCompanyPos >= 0 ? currentCompanyPos + 1 : "--";
  const currentTotalDrivers = Math.max(currentPeriodStatsArray.length, 1);
  const currentDiffToNext =
    currentCompanyPos > 0
      ? currentPeriodStatsArray[currentCompanyPos - 1].val -
        (currentCompanyPos >= 0
          ? currentPeriodStatsArray[currentCompanyPos].val
          : 0)
      : 0;"""

    if old_ranking in content:
        content = content.replace(old_ranking, new_ranking)
        print("Replaced ranking logic in Company")
    else:
        print("Old ranking logic not found in Company")

    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)

fix_company()
