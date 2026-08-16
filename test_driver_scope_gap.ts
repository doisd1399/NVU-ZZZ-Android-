import assert from "node:assert/strict";
import { buildDriverRankingContext } from "./src/lib/performanceEngine";
import { normalizeTrip } from "./src/lib/tripNormalizer";

const start = new Date("2026-07-01T00:00:00.000Z");
const end = new Date("2026-07-31T23:59:59.999Z");

function trip(id: string, driverId: string, companyId: string, name: string, value: number) {
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

const trips = [
  trip("c1", "current", "a", "Motorista Atual", 78_200),
  trip("a4", "internal-next", "a", "Próximo Interno", 91_310),
  trip("a3", "internal-third", "a", "Terceiro Interno", 120_000),
  trip("a2", "internal-second", "a", "Segundo Interno", 130_000),
  trip("a1", "internal-first", "a", "Líder Interno", 140_000),
  trip("b1", "global-next", "b", "Próximo Global", 80_000),
  trip("b2", "global-other-1", "b", "Global 1", 200_000),
  trip("b3", "global-other-2", "b", "Global 2", 190_000),
  trip("b4", "global-other-3", "b", "Global 3", 180_000),
  trip("b5", "global-other-4", "b", "Global 4", 170_000),
  trip("b6", "global-other-5", "b", "Global 5", 160_000),
  trip("b7", "global-other-6", "b", "Global 6", 150_000),
  trip("b8", "global-other-7", "b", "Global 7", 145_000),
];

const internal = buildDriverRankingContext(trips, {
  scope: "internal",
  startDate: start,
  endDate: end,
  driverId: "current",
  companyId: "a",
});
const global = buildDriverRankingContext(trips, {
  scope: "global",
  startDate: start,
  endDate: end,
  driverId: "current",
  companyId: "a",
});

assert.equal(internal.position, 5);
assert.equal(internal.differenceToNext, 13_110);
assert.equal(internal.nextCompetitor?.name, "Próximo Interno");
assert.equal(global.position, 13);
assert.equal(global.differenceToNext, 1_800);
assert.equal(global.nextCompetitor?.name, "Próximo Global");
assert.notEqual(internal.nextCompetitor?.id, global.nextCompetitor?.id);
console.log("Diferença e próximo colocado respeitam Interno/Global: OK", {
  internal: { gap: internal.differenceToNext, next: internal.nextCompetitor?.name },
  global: { gap: global.differenceToNext, next: global.nextCompetitor?.name },
});
