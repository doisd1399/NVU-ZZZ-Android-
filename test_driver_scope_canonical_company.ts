import assert from "node:assert/strict";
import { mergeCompanyTripsForRanking } from "./src/lib/companyScope";
import { buildDriverRankingContext } from "./src/lib/performanceEngine";
import { normalizeTrip } from "./src/lib/tripNormalizer";

const start = new Date("2026-07-01T00:00:00.000Z");
const end = new Date("2026-07-31T23:59:59.999Z");

function trip(id: string, driverId: string, companyField: "empresaId" | "companyId", companyId: string, name: string, value: number) {
  return normalizeTrip({
    id,
    status: "completed",
    driverId,
    motoristaNome: name,
    [companyField]: companyId,
    completedAt: "2026-07-10T12:00:00.000Z",
    valor: value,
  } as any);
}

// Company listener sees only empresaId records. The global listener also sees
// a legacy companyId record belonging to the same next competitor.
const companyListenerTrips = [
  trip("current", "biel", "empresaId", "tr", "Biel", 78_200),
  trip("next-a", "andriel", "empresaId", "tr", "Andriel Dos", 80_000),
];
const globalTrips = [
  ...companyListenerTrips,
  trip("next-b", "andriel", "companyId", "tr", "Andriel Dos", 11_310),
  trip("tr-1", "tr-1", "empresaId", "tr", "TR 1", 140_000),
  trip("tr-2", "tr-2", "empresaId", "tr", "TR 2", 130_000),
  trip("tr-3", "tr-3", "empresaId", "tr", "TR 3", 120_000),
  trip("g-1", "g-1", "empresaId", "dc", "Global 1", 220_000),
  trip("g-2", "g-2", "empresaId", "dc", "Global 2", 210_000),
  trip("g-3", "g-3", "empresaId", "dc", "Global 3", 200_000),
  trip("g-4", "g-4", "empresaId", "dc", "Global 4", 190_000),
  trip("g-5", "g-5", "empresaId", "dc", "Global 5", 180_000),
  trip("low-1", "low-1", "empresaId", "dc", "Low 1", 70_000),
  trip("low-2", "low-2", "empresaId", "dc", "Low 2", 60_000),
  trip("low-3", "low-3", "empresaId", "dc", "Low 3", 50_000),
  trip("low-4", "low-4", "empresaId", "dc", "Low 4", 40_000),
];

const canonicalCompanyTrips = mergeCompanyTripsForRanking({
  globalTrips,
  companyTrips: companyListenerTrips,
  companyId: "tr",
});

const internal = buildDriverRankingContext(canonicalCompanyTrips, {
  scope: "internal", startDate: start, endDate: end, driverId: "biel", companyId: "tr",
});
const global = buildDriverRankingContext(globalTrips, {
  scope: "global", startDate: start, endDate: end, driverId: "biel", companyId: "tr",
});

assert.equal(internal.position, 5);
assert.equal(global.position, 10);
assert.equal(internal.nextCompetitor?.id, "andriel");
assert.equal(global.nextCompetitor?.id, "andriel");
assert.equal(internal.nextCompetitor?.earnings, 91_310);
assert.equal(global.nextCompetitor?.earnings, 91_310);
assert.equal(internal.differenceToNext, 13_110);
assert.equal(global.differenceToNext, 13_110);
assert.equal(canonicalCompanyTrips.length, 6);

console.log("Fonte canônica mantém o mesmo ganho para o mesmo concorrente: OK", {
  internal: { position: internal.position, gap: internal.differenceToNext },
  global: { position: global.position, gap: global.differenceToNext },
});
