import assert from "node:assert/strict";
import {
  resolveOperationalCompanyId,
  resolveViewedDriverCompanyId,
} from "./src/lib/companyScope";
import { buildDriverRankingContext } from "./src/lib/performanceEngine";
import { normalizeTrip } from "./src/lib/tripNormalizer";

assert.equal(
  resolveOperationalCompanyId({
    activeCompanyId: "dc",
    currentUserCompanyId: "dc",
    seniorCompanyId: "tr",
    seniorAccess: true,
  }),
  "dc",
  "A empresa escolhida pelo usuário deve vencer um seniorCompanyId antigo",
);

assert.equal(
  resolveViewedDriverCompanyId({
    routeCompanyId: "tr",
    activeJobCompanyId: "tr",
    latestTripCompanyId: "tr",
    activeMembershipCompanyId: "dc",
    userCompanyId: "dc",
    fallbackActiveCompanyId: "dc",
  }),
  "tr",
  "O perfil isolado deve usar a empresa explicitamente navegada",
);

const start = new Date("2026-07-01T00:00:00.000Z");
const end = new Date("2026-07-31T23:59:59.999Z");
const trip = (id: string, driverId: string, companyId: string, name: string, value: number) =>
  normalizeTrip({
    id, status: "completed", driverId, motoristaNome: name, empresaId: companyId,
    completedAt: "2026-07-10T12:00:00.000Z", valor: value,
  } as any);
const trips = [
  trip("f", "fabio", "tr", "Fabio", 78_200),
  trip("ti", "tr-next", "tr", "Proximo TR", 90_000),
  trip("tg", "global-next", "dc", "Proximo Global", 80_000),
  trip("l1", "leader-tr", "tr", "Lider TR", 150_000),
  trip("l2", "leader-dc", "dc", "Lider Global", 200_000),
];
const internal = buildDriverRankingContext(trips, {
  scope: "internal", startDate: start, endDate: end, driverId: "fabio", companyId: "tr",
});
const global = buildDriverRankingContext(trips, {
  scope: "global", startDate: start, endDate: end, driverId: "fabio", companyId: "tr",
});
assert.equal(internal.nextCompetitor?.id, "tr-next");
assert.equal(internal.differenceToNext, 11_800);
assert.equal(global.nextCompetitor?.id, "global-next");
assert.equal(global.differenceToNext, 1_800);
assert.notEqual(internal.differenceToNext, global.differenceToNext);
console.log("Escopo corporativo e motorista isolado: OK", { internal: internal.differenceToNext, global: global.differenceToNext });
