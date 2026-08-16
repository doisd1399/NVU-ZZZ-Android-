import assert from "node:assert/strict";
import {
  getDriverIdsForPeriod,
  groupMetricsByDriver,
  filterTripsBySimulator,
} from "./src/lib/metricsEngine";
import { normalizeTrip } from "./src/lib/tripNormalizer";

const companyId = "company-a";
const start = new Date("2026-07-12T00:00:00");
const end = new Date("2026-07-18T23:59:59.999");

const trips = [
  normalizeTrip({
    id: "trip-active",
    status: "completed",
    driverId: "driver-active",
    empresaId: companyId,
    completedAt: "2026-07-13T10:00:00",
    valor: 100,
  } as any),
  normalizeTrip({
    id: "trip-former",
    status: "completed",
    motoristaId: "driver-former",
    empresaId: companyId,
    completedAt: "2026-07-15T10:00:00",
    valor: 250,
  } as any),
  normalizeTrip({
    id: "trip-outside-period",
    status: "completed",
    motoristaId: "driver-outside",
    empresaId: companyId,
    completedAt: "2026-07-20T10:00:00",
    valor: 900,
  } as any),
  normalizeTrip({
    id: "trip-other-simulator",
    status: "completed",
    motoristaId: "driver-other-simulator",
    empresaId: "company-other",
    simuladorNome: "ATS",
    completedAt: "2026-07-14T10:00:00",
    valor: 999,
  } as any),
];

const currentMembers = [
  {
    userId: "driver-active",
    companyId,
    status: "active",
    roles: ["driver"],
  },
  {
    userId: "driver-no-trips",
    companyId,
    status: "active",
    roles: ["driver"],
  },
  {
    userId: "driver-former",
    companyId,
    status: "inactive",
    roles: ["driver"],
  },
];

const simulatorTrips = filterTripsBySimulator(
  trips,
  "ETS2",
  [{ id: companyId, simulatorName: "ETS2" }, { id: "company-other", simulatorName: "ATS" }],
);

const periodDriverIds = getDriverIdsForPeriod(
  simulatorTrips,
  start,
  end,
  companyId,
  currentMembers,
);

assert.deepEqual(
  [...periodDriverIds].sort(),
  ["driver-active", "driver-former", "driver-no-trips"].sort(),
  "A população deve unir ativos atuais e ex-motoristas com viagem no período",
);
assert.equal(
  periodDriverIds.has("driver-outside"),
  false,
  "Viagens fora do período não devem inserir o motorista no ranking",
);
assert.equal(
  periodDriverIds.has("driver-other-simulator"),
  false,
  "Motoristas de outro simulador não devem entrar na população",
);

const ranking = groupMetricsByDriver(
  simulatorTrips,
  start,
  end,
  companyId,
  [
    { id: "driver-active", name: "Ativo" },
    { id: "driver-former", name: "Ex-motorista" },
    { id: "driver-no-trips", name: "Sem viagens" },
  ],
  "Todos os simuladores",
  [{ id: companyId, simulatorName: "ETS2" }],
  currentMembers,
);

assert.deepEqual(
  ranking.map((driver) => driver.id),
  ["driver-former", "driver-active", "driver-no-trips"],
  "O ex-motorista deve permanecer no ranking e ser ordenado pelas métricas do período",
);
assert.equal(ranking[0].trips, 1);
assert.equal(ranking[0].val, 250);

console.log("Fase de população histórica do ranking: OK");
