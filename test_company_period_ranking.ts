import assert from "node:assert/strict";
import {
  getCompanyIdsForPeriod,
  groupMetricsByCompany,
  filterTripsBySimulator,
} from "./src/lib/metricsEngine";
import { normalizeTrip } from "./src/lib/tripNormalizer";

const start = new Date("2026-07-12T00:00:00");
const end = new Date("2026-07-18T23:59:59.999");

const trips = [
  normalizeTrip({
    id: "trip-current",
    status: "completed",
    empresaId: "company-current",
    completedAt: "2026-07-13T10:00:00",
    valor: 100,
  } as any),
  normalizeTrip({
    id: "trip-former",
    status: "completed",
    empresaId: "company-former",
    simuladorNome: "ETS2",
    completedAt: "2026-07-15T10:00:00",
    valor: 300,
  } as any),
  normalizeTrip({
    id: "trip-outside-period",
    status: "completed",
    empresaId: "company-outside",
    completedAt: "2026-07-20T10:00:00",
    valor: 900,
  } as any),
  normalizeTrip({
    id: "trip-other-simulator",
    status: "completed",
    empresaId: "company-other-simulator",
    simuladorNome: "ATS",
    completedAt: "2026-07-14T10:00:00",
    valor: 999,
  } as any),
];

const currentCompanies = [
  { id: "company-current", companyName: "Empresa atual", simulatorName: "ETS2" },
  { id: "company-no-trips", companyName: "Empresa sem viagens", simulatorName: "ETS2" },
  { id: "company-other-simulator", companyName: "Empresa ATS", simulatorName: "ATS" },
];

const simulatorTrips = filterTripsBySimulator(trips, "ETS2", currentCompanies);
const currentEts2Companies = currentCompanies.filter(
  (company) => company.simulatorName === "ETS2",
);

const periodCompanyIds = getCompanyIdsForPeriod(
  simulatorTrips,
  start,
  end,
  currentEts2Companies,
);

assert.deepEqual(
  [...periodCompanyIds].sort(),
  ["company-current", "company-former", "company-no-trips"].sort(),
  "A população deve unir empresas atuais e empresas com viagens no período",
);
assert.equal(
  periodCompanyIds.has("company-outside"),
  false,
  "Viagens fora do período não devem inserir a empresa no ranking",
);
assert.equal(
  periodCompanyIds.has("company-other-simulator"),
  false,
  "Empresas de outro simulador não devem entrar na população",
);

const ranking = groupMetricsByCompany(
  trips,
  start,
  end,
  "ETS2",
  currentCompanies,
);

assert.deepEqual(
  ranking.map((company) => company.id),
  ["company-former", "company-current", "company-no-trips"],
  "A empresa histórica deve permanecer no ranking e empresas atuais sem viagens devem compor a população",
);
assert.equal(ranking[0].trips, 1);
assert.equal(ranking[0].val, 300);
assert.equal(ranking[2].trips, 0);

console.log("Fase de população histórica do ranking de empresas: OK");
