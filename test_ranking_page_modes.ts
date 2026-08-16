import assert from "node:assert/strict";
import { normalizeTrip } from "./src/lib/tripNormalizer";
import { buildDriverRankingPageData } from "./src/lib/rankingPageEngine";
import { buildDriverRankingContext } from "./src/lib/performanceEngine";

const startDate = new Date("2026-07-01T00:00:00");
const endDate = new Date("2026-07-31T23:59:59");
const rawTrips = [
  { id: "a1", status: "concluida", completedAt: "2026-07-05T12:00:00", valor: 100, motoristaId: "a", motoristaNome: "Ana Silva", empresaId: "c1", simuladorNome: "GTO" },
  { id: "a2", status: "concluida", completedAt: "2026-07-06T12:00:00", valor: 80, motoristaId: "a", motoristaNome: "Ana Silva", empresaId: "c2", simuladorNome: "GTO" },
  { id: "b1", status: "concluida", completedAt: "2026-07-07T12:00:00", valor: 150, motoristaId: "b", motoristaNome: "Bruno Lima", empresaId: "c1", simuladorNome: "GTO" },
  { id: "c1", status: "concluida", completedAt: "2026-07-08T12:00:00", valor: 300, motoristaId: "c", motoristaNome: "Carla Souza", empresaId: "c2", simuladorNome: "GTO" },
  { id: "x1", status: "concluida", completedAt: "2026-07-08T12:00:00", valor: 999, motoristaId: "x", motoristaNome: "Outro Sim", empresaId: "c3", simuladorNome: "OUTRO" },
].map((trip) => normalizeTrip(trip as any));

const companies = [
  { id: "c1", name: "DC", simulatorName: "GTO" },
  { id: "c2", name: "TR", simulatorName: "GTO" },
  { id: "c3", name: "X", simulatorName: "OUTRO" },
];
const users = [
  { id: "a", name: "Ana Silva", profilePhotoURL: "ana.jpg" },
  { id: "b", name: "Bruno Lima", profilePhotoURL: "bruno.jpg" },
  { id: "c", name: "Carla Souza", profilePhotoURL: "carla.jpg" },
];

const internal = buildDriverRankingPageData({
  trips: rawTrips,
  startDate,
  endDate,
  scope: "internal",
  companyId: "c1",
  simulator: "GTO",
  companies,
  users,
});
const global = buildDriverRankingPageData({
  trips: rawTrips,
  startDate,
  endDate,
  scope: "global",
  simulator: "GTO",
  companies,
  users,
});

assert.deepEqual(internal.map((item) => item.id), ["a", "b"]);
assert.deepEqual(global.map((item) => item.id), ["c", "a", "b"]);
// Ana's canonical total is identical in Internal and Global even though she
// worked for two companies during the period.
assert.equal(internal.find((item) => item.id === "a")?.val, 180);
assert.equal(global.find((item) => item.id === "a")?.val, 180);
assert.equal(global.some((item) => item.id === "x"), false);

const profileGlobal = buildDriverRankingContext(rawTrips.filter((trip) => trip.simuladorNome === "GTO"), {
  scope: "global",
  startDate,
  endDate,
  driverId: "a",
});
assert.deepEqual(global.map((item) => item.id), profileGlobal.ranking.map((item) => item.id));
assert.equal(global.findIndex((item) => item.id === "a") + 1, profileGlobal.position);

console.log("ranking page modes: OK", {
  internal: internal.map((item) => [item.id, item.val]),
  global: global.map((item) => [item.id, item.val]),
  profilePosition: profileGlobal.position,
});
