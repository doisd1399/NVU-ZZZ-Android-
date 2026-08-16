import re

def fix_driver():
    filepath = "src/components/DriverPerformanceCard.tsx"
    with open(filepath, "r", encoding="utf-8") as f:
        content = f.read()

    old_ranking = """  const currentPeriodStatsMap: Record<string, { trips: number; val: number }> =
    {};
  normalizedHistorico.forEach((trip) => {
    if (trip.metricDate >= sDate && trip.metricDate <= eDate) {
      if (
        activeCompanyId &&
        trip.empresaId &&
        trip.empresaId !== activeCompanyId
      ) {
        return;
      }
      const mId = trip.motoristaId;
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

  const currentDriverPos = currentPeriodStatsArray.findIndex(
    (d) => d.id === driverId,
  );
  const currentPosition = currentDriverPos >= 0 ? currentDriverPos + 1 : "--";
  const currentTotalDrivers =
    allCompanyMembers && allCompanyMembers.length > 0
      ? allCompanyMembers.length
      : Math.max(currentPeriodStatsArray.length, 1);
  const currentDiffToNext =
    currentDriverPos > 0
      ? currentPeriodStatsArray[currentDriverPos - 1].val -
        (currentDriverPos >= 0
          ? currentPeriodStatsArray[currentDriverPos].val
          : 0)
      : 0;"""

    new_ranking = """  const currentPeriodStatsMap: Record<string, { trips: number; val: number }> = {};
  
  if (allCompanyMembers && allCompanyMembers.length > 0) {
    allCompanyMembers.forEach(m => {
      currentPeriodStatsMap[m.id] = { trips: 0, val: 0 };
    });
  }

  normalizedHistorico.forEach((trip) => {
    if (trip.metricDate >= sDate && trip.metricDate <= eDate) {
      if (
        activeCompanyId &&
        trip.empresaId &&
        trip.empresaId !== activeCompanyId
      ) {
        return;
      }
      const mId = trip.motoristaId || trip.driverId; // fallback
      if (!mId) return;
      if (!currentPeriodStatsMap[mId])
        currentPeriodStatsMap[mId] = { trips: 0, val: 0 };
      currentPeriodStatsMap[mId].trips += 1;
      currentPeriodStatsMap[mId].val += trip.normalizedValor;
    }
  });

  if (!currentPeriodStatsMap[driverId]) {
    currentPeriodStatsMap[driverId] = { trips: 0, val: 0 };
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

  const currentDriverPos = currentPeriodStatsArray.findIndex(
    (d) => d.id === driverId,
  );
  const currentPosition = currentDriverPos >= 0 ? currentDriverPos + 1 : "--";
  const currentTotalDrivers = Math.max(currentPeriodStatsArray.length, 1);
  const currentDiffToNext =
    currentDriverPos > 0
      ? currentPeriodStatsArray[currentDriverPos - 1].val -
        (currentDriverPos >= 0
          ? currentPeriodStatsArray[currentDriverPos].val
          : 0)
      : 0;"""

    if old_ranking in content:
        content = content.replace(old_ranking, new_ranking)
        print("Replaced ranking logic in Driver")
    else:
        print("Old ranking logic not found in Driver")

    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)

fix_driver()
