import assert from "node:assert/strict";
import { normalizeTrip } from "./src/lib/tripNormalizer";
import {
  buildCanonicalDriverRankingContext,
  buildDriverRankingPageData,
} from "./src/lib/rankingPageEngine";

const startDate = new Date("2026-07-01T00:00:00.000Z");
const endDate = new Date("2026-07-31T23:59:59.999Z");
const companies = [
  { id: "dc", companyName: "DC Transportes", simulatorName: "GTO" },
  { id: "tr", companyName: "TR Souza", simulatorName: "GTO" },
  { id: "out", companyName: "Outro", simulatorName: "OUTRO" },
];

function trip(id: string, driverId: string, companyId: string, name: string, value: number) {
  return normalizeTrip({
    id,
    status: "completed",
    driverId,
    motoristaNome: name,
    empresaId: companyId,
    simuladorNome: companyId === "out" ? "OUTRO" : "GTO",
    completedAt: "2026-07-10T12:00:00.000Z",
    valor: value,
  } as any);
}

const trips = [
  trip("david", "david", "dc", "David Canto", 270_472),
  trip("carlos-dc", "carlos", "dc", "Carlos Emanuel", 304_627),
  trip("carlos-tr", "carlos", "tr", "Carlos Emanuel", 80_400),
  trip("dc-high", "dc-high", "dc", "Líder DC", 600_000),
  trip("global-near", "global-near", "tr", "Próximo Global", 280_000),
  trip("tr-high", "tr-high", "tr", "Líder TR", 800_000),
  trip("other", "other", "out", "Outro Sim", 2_000_000),
];

for (const scope of ["internal", "global"] as const) {
  const companyId = scope === "internal" ? "dc" : undefined;
  const page = buildDriverRankingPageData({
    trips,
    startDate,
    endDate,
    scope,
    companyId,
    simulator: "GTO",
    companies,
  });
  const profile = buildCanonicalDriverRankingContext({
    trips,
    startDate,
    endDate,
    scope,
    companyId,
    simulator: "GTO",
    companies,
    driverId: "david",
  });

  assert.deepEqual(page.map((item) => item.id), profile.ranking.map((item) => item.id));
  const pagePosition = page.findIndex((item) => item.id === "david") + 1;
  assert.equal(profile.position, pagePosition);
  const pageNext = pagePosition > 1 ? page[pagePosition - 2] : null;
  assert.equal(profile.nextCompetitor?.id ?? null, pageNext?.id ?? null);
  assert.equal(
    profile.differenceToNext,
    pageNext ? pageNext.val - (page[pagePosition - 1]?.val || 0) : 0,
  );
}

const internal = buildCanonicalDriverRankingContext({
  trips, startDate, endDate, scope: "internal", companyId: "dc",
  simulator: "GTO", companies, driverId: "david",
});
const global = buildCanonicalDriverRankingContext({
  trips, startDate, endDate, scope: "global",
  simulator: "GTO", companies, driverId: "david",
});
assert.equal(internal.nextCompetitor?.id, "carlos");
assert.equal(internal.differenceToNext, 114_555);
assert.equal(global.nextCompetitor?.id, "global-near");
assert.equal(global.differenceToNext, 9_528);
assert.equal(global.ranking.some((item) => item.id === "other"), false);

console.log("Perfil e página de ranking alinhados: OK", {
  internal: { next: internal.nextCompetitor?.id, gap: internal.differenceToNext },
  global: { next: global.nextCompetitor?.id, gap: global.differenceToNext },
});
