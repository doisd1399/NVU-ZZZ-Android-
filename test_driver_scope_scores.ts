import assert from "node:assert/strict";
import { buildDriverRankingContext } from "./src/lib/performanceEngine";
import { normalizeTrip } from "./src/lib/tripNormalizer";

const start = new Date("2026-07-01T00:00:00.000Z");
const end = new Date("2026-07-31T23:59:59.999Z");

const makeTrips = (
  driverId: string,
  companyId: string,
  count: number,
  value: number,
  date = "2026-07-10T12:00:00.000Z",
) => Array.from({ length: count }, (_, index) => normalizeTrip({
  id: `${driverId}-${index}`,
  status: "completed",
  driverId,
  empresaId: companyId,
  completedAt: date,
  valor: value,
} as any));

const trips = [
  ...makeTrips("driver-current", "company-a", 20, 100),
  ...makeTrips("driver-team-leader", "company-a", 40, 120),
  // Ex-motorista sem vínculo atual: continua entrando porque trabalhou no período.
  ...makeTrips("driver-former", "company-a", 10, 90),
  ...makeTrips("driver-global-leader", "company-b", 100, 180),
  ...makeTrips("driver-global-second", "company-b", 70, 160),
  // Fora do período: não pode influenciar nenhum score.
  ...makeTrips("driver-outside", "company-b", 500, 999, "2026-08-10T12:00:00.000Z"),
];

const internal = buildDriverRankingContext(trips, {
  scope: "internal",
  startDate: start,
  endDate: end,
  driverId: "driver-current",
  companyId: "company-a",
});

const global = buildDriverRankingContext(trips, {
  scope: "global",
  startDate: start,
  endDate: end,
  driverId: "driver-current",
  companyId: "company-a",
});

assert.equal(internal.totalParticipants, 3, "Interno deve usar somente a equipe que participou");
assert.equal(global.totalParticipants, 5, "Global deve usar todos os participantes do simulador");
assert.equal(internal.position, 2);
assert.equal(global.position, 4);
assert.ok(internal.viagensScore > global.viagensScore, "Viagens deve responder ao escopo");
assert.ok(internal.ganhosScore > global.ganhosScore, "Ganhos deve responder ao escopo");
assert.ok(internal.ritmoOperacionalScore > global.ritmoOperacionalScore, "Ritmo deve responder ao escopo");
assert.ok(internal.finalIndex > global.finalIndex, "Índice final deve responder ao escopo");

for (const value of [
  internal.viagensScore,
  internal.ganhosScore,
  internal.ritmoOperacionalScore,
  internal.finalIndex,
  global.viagensScore,
  global.ganhosScore,
  global.ritmoOperacionalScore,
  global.finalIndex,
]) {
  assert.ok(value >= 0 && value <= 100, `Score fora do intervalo: ${value}`);
}

console.log("Ranking interno/global altera Ritmo, Viagens, Ganhos e Índice: OK", {
  internal: {
    position: internal.position,
    total: internal.totalParticipants,
    ritmo: internal.ritmoOperacionalScore,
    viagens: internal.viagensScore,
    ganhos: internal.ganhosScore,
    indice: internal.finalIndex,
  },
  global: {
    position: global.position,
    total: global.totalParticipants,
    ritmo: global.ritmoOperacionalScore,
    viagens: global.viagensScore,
    ganhos: global.ganhosScore,
    indice: global.finalIndex,
  },
});
