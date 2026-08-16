import assert from "node:assert/strict";
import { buildDriverRankingContext } from "./src/lib/performanceEngine";
import { normalizeTrip } from "./src/lib/tripNormalizer";

const start = new Date("2026-07-01T00:00:00.000Z");
const end = new Date("2026-07-31T23:59:59.999Z");

function trip(
  id: string,
  driverId: string,
  companyId: string,
  name: string,
  value: number,
) {
  return normalizeTrip({
    id,
    status: "completed",
    driverId,
    motoristaNome: name,
    empresaId: companyId,
    completedAt: "2026-07-10T12:00:00.000Z",
    valor: value,
  } as any);
}

// Carlos is eligible for the internal DC ranking because he worked for DC in
// the period, but he also has another trip in TR Souza. His canonical period
// earnings must be identical in Internal and Global; only the participant set
// and position are allowed to change.
const trips = [
  trip("david", "david", "dc", "David Canto", 270_472),
  trip("carlos-dc", "carlos", "dc", "Carlos Emanuel", 304_627),
  trip("carlos-tr", "carlos", "tr", "Carlos Emanuel", 80_400),
  trip("dc-1", "dc-1", "dc", "DC 1", 500_000),
  trip("dc-2", "dc-2", "dc", "DC 2", 470_000),
  trip("dc-3", "dc-3", "dc", "DC 3", 430_000),
  trip("dc-4", "dc-4", "dc", "DC 4", 400_000),
  trip("dc-5", "dc-5", "dc", "DC 5", 390_000),
  trip("tr-1", "tr-1", "tr", "TR 1", 700_000),
  trip("tr-2", "tr-2", "tr", "TR 2", 650_000),
  trip("tr-3", "tr-3", "tr", "TR 3", 600_000),
  trip("tr-4", "tr-4", "tr", "TR 4", 550_000),
  trip("low-1", "low-1", "tr", "Low 1", 200_000),
];

const internal = buildDriverRankingContext(trips, {
  scope: "internal",
  startDate: start,
  endDate: end,
  driverId: "david",
  companyId: "dc",
});
const global = buildDriverRankingContext(trips, {
  scope: "global",
  startDate: start,
  endDate: end,
  driverId: "david",
  companyId: "dc",
});

assert.equal(internal.nextCompetitor?.id, "carlos");
assert.equal(global.nextCompetitor?.id, "carlos");
assert.equal(internal.nextCompetitor?.earnings, 385_027);
assert.equal(global.nextCompetitor?.earnings, 385_027);
assert.equal(internal.current.earnings, 270_472);
assert.equal(global.current.earnings, 270_472);
assert.equal(internal.differenceToNext, 114_555);
assert.equal(global.differenceToNext, 114_555);
assert.notEqual(internal.position, global.position);
assert.notEqual(internal.totalParticipants, global.totalParticipants);

console.log("Mesmo concorrente mantém total e diferença canônicos: OK", {
  internal: {
    position: internal.position,
    total: internal.totalParticipants,
    gap: internal.differenceToNext,
  },
  global: {
    position: global.position,
    total: global.totalParticipants,
    gap: global.differenceToNext,
  },
});
