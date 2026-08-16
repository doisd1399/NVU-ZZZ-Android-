import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

const db = admin.firestore();
const AGGREGATES_COLLECTION = "ranking_aggregates";
const CONTROLS_COLLECTION = "ranking_aggregate_controls";
// v4 keeps closed-period aggregates isolated from documents created before
// live/open periods became authoritative across every app surface.
const SCHEMA_VERSION = 4;
const PAGE_SIZE = 500;
const WRITE_BATCH_SIZE = 350;
const LOCK_TTL_MS = 10 * 60 * 1000;
const RECONCILE_AFTER_MS = 60 * 60 * 1000;
const RANKING_CHECKPOINT_VERSION = 4;
const FAILED_RETRY_COOLDOWN_MS = 15 * 60 * 1000;
const DATE_FIELDS = [
  "completedAt",
  "dataFechamento",
  "date",
  "dataLancamento",
  "createdAt",
] as const;

type PeriodType = "semana" | "mes";
type RankingCheckpointStage = "collecting" | "writing" | "completed";

type CompanyStat = {
  trips: number;
  val: number;
  name?: string;
};

type DriverStat = {
  trips: number;
  val: number;
  companyId?: string;
  name?: string;
};

type AggregateData = {
  companies: Record<string, CompanyStat>;
  drivers: Record<string, DriverStat>;
  sourceTripCount: number;
};

type PeriodDescriptor = {
  type: PeriodType;
  key: string;
  start: Date;
  end: Date;
};

type SimulatorDescriptor = {
  id: string;
  name: string;
  aliases: string[];
};

type Contribution = {
  simulatorId: string;
  companyId: string;
  companyName: string;
  driverId: string;
  driverName: string;
  value: number;
  date: Date;
};

type AggregateDelta = {
  simulatorId: string;
  period: PeriodDescriptor;
  companyDeltas: Map<string, CompanyStat>;
  driverDeltas: Map<string, DriverStat>;
  sourceTripDelta: number;
};

type EnsureInput = {
  simulatorId?: unknown;
  periodType?: unknown;
  periodKey?: unknown;
};

type EnsureResult = {
  success: true;
  status: "ready" | "rebuilt" | "in_progress";
  periodKey: string;
  rebuiltDocuments?: number;
  sourceTrips?: number;
};

function firstNonEmpty(...values: unknown[]): string {
  for (const value of values) {
    if (value === null || value === undefined) continue;
    const normalized = String(value).trim();
    if (normalized) return normalized;
  }
  return "";
}

function normalizeText(value: unknown): string {
  return String(value || "")
    .trim()
    .toLocaleLowerCase("pt-BR")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]/g, "");
}

function canonicalSimulatorAliasKey(value: unknown): string {
  const normalized = normalizeText(value);
  const aliases: Record<string, string> = {
    gto: "gto",
    globaltruckonline: "gto",
    wtds: "wtds",
    worldtruckdrivingsimulator: "wtds",
    wbds: "wbds",
    worldbusdrivingsimulator: "wbds",
    toe3: "toe3",
    truckersofeurope3: "toe3",
    ets2: "ets2",
    eurotrucksimulator2: "ets2",
    ats: "ats",
    americantrucksimulator: "ats",
    pbs: "pbs",
    protonbussimulator: "pbs",
  };
  return aliases[normalized] || normalized;
}

function isUnsupportedSimulatorAlias(value: unknown): boolean {
  return normalizeText(value) === "grandtrucksimulator";
}

function hasUnsupportedSimulatorIdentity(
  data: FirebaseFirestore.DocumentData | undefined,
): boolean {
  if (!data) return false;
  return [
    data.simulatorName,
    data.simuladorNome,
    data.simulator,
    data.simulador,
    data.name,
    data.nome,
    data.label,
    data.title,
    data.displayName,
  ].some(isUnsupportedSimulatorAlias);
}

function safeDocumentId(value: string): string {
  return value.replace(/[^a-zA-Z0-9%_.~-]/g, "_").slice(0, 1400);
}

function aggregateDocumentId(simulatorId: string, periodKey: string): string {
  return safeDocumentId(`v${SCHEMA_VERSION}__${encodeURIComponent(simulatorId)}__${periodKey}`);
}

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

function formatUtcDateKey(date: Date): string {
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

function periodFromKey(periodType: PeriodType, periodKey: string): PeriodDescriptor | null {
  if (periodType === "mes") {
    const match = /^mes_(\d{4})-(\d{2})$/.exec(periodKey);
    if (!match) return null;
    const year = Number(match[1]);
    const month = Number(match[2]);
    if (month < 1 || month > 12) return null;
    const start = new Date(Date.UTC(year, month - 1, 1));
    const nextMonth = new Date(Date.UTC(year, month, 1));
    return {
      type: periodType,
      key: periodKey,
      start,
      end: new Date(nextMonth.getTime() - 1),
    };
  }

  const match = /^semana_(\d{4})-(\d{2})-(\d{2})$/.exec(periodKey);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const start = new Date(Date.UTC(year, month - 1, day));
  if (
    start.getUTCFullYear() !== year ||
    start.getUTCMonth() + 1 !== month ||
    start.getUTCDate() !== day ||
    start.getUTCDay() !== 0
  ) {
    return null;
  }
  return {
    type: periodType,
    key: periodKey,
    start,
    end: new Date(start.getTime() + 7 * 24 * 60 * 60 * 1000 - 1),
  };
}

function periodsForDate(date: Date): PeriodDescriptor[] {
  const monthKey = `mes_${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}`;
  const weekStart = new Date(
    Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()),
  );
  weekStart.setUTCDate(weekStart.getUTCDate() - weekStart.getUTCDay());
  const weekKey = `semana_${formatUtcDateKey(weekStart)}`;
  const month = periodFromKey("mes", monthKey);
  const week = periodFromKey("semana", weekKey);
  return [week, month].filter((period): period is PeriodDescriptor => Boolean(period));
}

function parseDate(value: unknown): Date | null {
  if (!value) return null;
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value;
  if (value instanceof admin.firestore.Timestamp) {
    return (value as { toDate: () => Date }).toDate();
  }

  const timestampLike = value as { toDate?: () => Date; seconds?: unknown };
  if (typeof timestampLike?.toDate === "function") {
    const date = timestampLike.toDate();
    return Number.isNaN(date.getTime()) ? null : date;
  }
  if (typeof timestampLike?.seconds === "number") {
    const date = new Date(timestampLike.seconds * 1000);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  const parsed = new Date(value as string | number);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function tripMetricDate(data: FirebaseFirestore.DocumentData): Date | null {
  return parseDate(
    data.completedAt ||
      data.dataFechamento ||
      data.date ||
      data.dataLancamento ||
      data.createdAt,
  );
}

function parseTripValue(value: unknown): number {
  if (typeof value === "number") return Number.isFinite(value) ? value : 0;
  const raw = String(value ?? "").trim();
  if (!raw) return 0;
  const cleaned = raw.replace(/[^0-9,.-]/g, "");
  if (!cleaned) return 0;

  let normalized = cleaned;
  if (cleaned.includes(",") && cleaned.includes(".")) {
    normalized = cleaned.lastIndexOf(",") > cleaned.lastIndexOf(".")
      ? cleaned.replace(/\./g, "").replace(",", ".")
      : cleaned.replace(/,/g, "");
  } else if (cleaned.includes(",")) {
    normalized = cleaned.replace(/\./g, "").replace(",", ".");
  } else if ((cleaned.match(/\./g) || []).length > 1) {
    const lastDot = cleaned.lastIndexOf(".");
    normalized = cleaned.slice(0, lastDot).replace(/\./g, "") + cleaned.slice(lastDot);
  }

  const parsed = Number(normalized);
  return Number.isFinite(parsed) ? parsed : 0;
}

function isValidCompletedTrip(data: FirebaseFirestore.DocumentData): boolean {
  const status = normalizeText(data.status);
  const canceled =
    ["cancelado", "cancelada", "canceled", "cancelled", "excluido", "excluida"].includes(status) ||
    data.cancelado === true ||
    data.deleted === true ||
    data.softDeleted === true;
  if (canceled) return false;

  if (["concluida", "concluido", "completed", "finalizado", "finalizada", "entregue"].includes(status)) {
    return true;
  }

  return !status && Boolean(tripMetricDate(data));
}

function companyIdOf(data: FirebaseFirestore.DocumentData): string {
  return firstNonEmpty(data.companyId, data.empresaId, data.company_id, data.empresa_id);
}

function driverIdOf(data: FirebaseFirestore.DocumentData): string {
  return firstNonEmpty(data.driverId, data.motoristaId, data.motorista_id, data.userId, data.driver_id);
}

function companyNameOf(data: FirebaseFirestore.DocumentData): string {
  return firstNonEmpty(data.empresaNome, data.companyName, data.company_name);
}

function driverNameOf(data: FirebaseFirestore.DocumentData): string {
  return firstNonEmpty(data.motoristaNome, data.driverName, data.motorista_nome, data.nomeMotorista, data.driver_name);
}

function simulatorCandidates(data: FirebaseFirestore.DocumentData): string[] {
  return [
    data.simulatorId,
    data.simuladorId,
    data.simulatorKey,
    data.simuladorKey,
    data.simulatorName,
    data.simuladorNome,
    data.simulator,
    data.simulador,
  ]
    .map((value) => firstNonEmpty(value))
    .filter(Boolean);
}

let simulatorCatalogCache: {
  expiresAt: number;
  descriptors: SimulatorDescriptor[];
  byAlias: Map<string, string>;
  ids: Set<string>;
} | null = null;

async function loadSimulatorCatalog() {
  if (simulatorCatalogCache && simulatorCatalogCache.expiresAt > Date.now()) {
    return simulatorCatalogCache;
  }

  const snapshot = await db.collection("simulators").get();
  const descriptors: SimulatorDescriptor[] = [];
  snapshot.docs.forEach((document) => {
    const data = document.data();
    const id = document.id;
    const semanticAliases = [
      data.name,
      data.nome,
      data.label,
      data.title,
      data.displayName,
      data.code,
      data.slug,
      data.key,
    ];

    // "Grand Truck Simulator" is not an official NVU simulator. Reject an
    // accidental catalog document before its opaque Firestore ID can make it
    // look valid or merge it into GTO. GTO means only Global Truck Online.
    if (semanticAliases.some(isUnsupportedSimulatorAlias)) return;

    const name = firstNonEmpty(data.name, data.nome, data.label, data.title, id);
    const aliases = Array.from(new Set([
      id,
      name,
      firstNonEmpty(data.code),
      firstNonEmpty(data.slug),
      firstNonEmpty(data.key),
      firstNonEmpty(data.simulatorId),
      firstNonEmpty(data.simuladorId),
    ].filter((value) => Boolean(value) && !isUnsupportedSimulatorAlias(value))));
    descriptors.push({ id, name, aliases });
  });

  const byAlias = new Map<string, string>();
  const ids = new Set<string>();
  descriptors.forEach((descriptor) => {
    ids.add(descriptor.id);
    descriptor.aliases.forEach((alias) => {
      byAlias.set(alias, descriptor.id);
      const normalized = canonicalSimulatorAliasKey(alias);
      if (normalized) byAlias.set(normalized, descriptor.id);
    });
  });

  simulatorCatalogCache = {
    expiresAt: Date.now() + 5 * 60 * 1000,
    descriptors,
    byAlias,
    ids,
  };
  return simulatorCatalogCache;
}

function resolveSimulatorFromRecords(
  trip: FirebaseFirestore.DocumentData,
  company: FirebaseFirestore.DocumentData | undefined,
  catalog: Awaited<ReturnType<typeof loadSimulatorCatalog>>,
): string {
  if (hasUnsupportedSimulatorIdentity(company)) return "";
  if (!company && hasUnsupportedSimulatorIdentity(trip)) return "";
  // The current company catalog is authoritative. Trip snapshots remain a
  // fallback only for legacy records whose company document has no simulator.
  const candidates = [
    ...(company ? simulatorCandidates(company) : []),
    ...simulatorCandidates(trip),
  ];

  for (const candidate of candidates) {
    if (isUnsupportedSimulatorAlias(candidate)) continue;
    if (catalog.ids.has(candidate)) return candidate;
    const exact = catalog.byAlias.get(candidate);
    if (exact) return exact;
    const normalized = canonicalSimulatorAliasKey(candidate);
    const resolved = normalized ? catalog.byAlias.get(normalized) : undefined;
    if (resolved) return resolved;
  }

  return "";
}

async function contributionFromData(
  data: FirebaseFirestore.DocumentData | undefined,
): Promise<Contribution | null> {
  if (!data || !isValidCompletedTrip(data)) return null;
  const date = tripMetricDate(data);
  const companyId = companyIdOf(data);
  const driverId = driverIdOf(data);
  if (!date || !companyId || !driverId) return null;

  const [catalog, companySnapshot] = await Promise.all([
    loadSimulatorCatalog(),
    db.collection("frotas").doc(companyId).get(),
  ]);
  const company = companySnapshot.exists ? companySnapshot.data() : undefined;
  if (!isEligibleCompany(company)) return null;
  const simulatorId = resolveSimulatorFromRecords(data, company, catalog);
  if (!simulatorId) return null;

  return {
    simulatorId,
    companyId,
    companyName: companyNameOf(data) || firstNonEmpty(company?.companyName, company?.name),
    driverId,
    driverName: driverNameOf(data),
    value: Math.max(0, parseTripValue(data.valor ?? data.value ?? data.totalValue ?? data.ganho)),
    date,
  };
}

function contributionSignature(contribution: Contribution | null): string {
  if (!contribution) return "";
  return [
    contribution.simulatorId,
    contribution.companyId,
    contribution.driverId,
    contribution.value,
    contribution.date.getTime(),
  ].join("|");
}

function deltaKey(simulatorId: string, periodKey: string): string {
  return `${simulatorId}|${periodKey}`;
}

function ensureDelta(
  deltas: Map<string, AggregateDelta>,
  simulatorId: string,
  period: PeriodDescriptor,
): AggregateDelta {
  const key = deltaKey(simulatorId, period.key);
  const existing = deltas.get(key);
  if (existing) return existing;
  const created: AggregateDelta = {
    simulatorId,
    period,
    companyDeltas: new Map(),
    driverDeltas: new Map(),
    sourceTripDelta: 0,
  };
  deltas.set(key, created);
  return created;
}

function addContributionDelta(
  deltas: Map<string, AggregateDelta>,
  contribution: Contribution,
  direction: 1 | -1,
) {
  periodsForDate(contribution.date).forEach((period) => {
    const delta = ensureDelta(deltas, contribution.simulatorId, period);
    const company = delta.companyDeltas.get(contribution.companyId) || {
      trips: 0,
      val: 0,
      name: contribution.companyName || undefined,
    };
    company.trips += direction;
    company.val += direction * contribution.value;
    if (!company.name && contribution.companyName) company.name = contribution.companyName;
    delta.companyDeltas.set(contribution.companyId, company);

    const driver = delta.driverDeltas.get(contribution.driverId) || {
      trips: 0,
      val: 0,
      companyId: contribution.companyId,
      name: contribution.driverName || undefined,
    };
    driver.trips += direction;
    driver.val += direction * contribution.value;
    if (direction > 0) driver.companyId = contribution.companyId;
    if (!driver.name && contribution.driverName) driver.name = contribution.driverName;
    delta.driverDeltas.set(contribution.driverId, driver);
    delta.sourceTripDelta += direction;
  });
}

function parseCompanyMap(value: unknown): Record<string, CompanyStat> {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  const result: Record<string, CompanyStat> = {};
  Object.entries(value as Record<string, unknown>).forEach(([id, raw]) => {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) return;
    const record = raw as Record<string, unknown>;
    result[id] = {
      trips: Math.max(0, Math.trunc(Number(record.trips) || 0)),
      val: Math.max(0, Number(record.val) || 0),
      name: firstNonEmpty(record.name) || undefined,
    };
  });
  return result;
}

function parseDriverMap(value: unknown): Record<string, DriverStat> {
  if (!value || typeof value !== "object" || Array.isArray(value)) return {};
  const result: Record<string, DriverStat> = {};
  Object.entries(value as Record<string, unknown>).forEach(([id, raw]) => {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) return;
    const record = raw as Record<string, unknown>;
    result[id] = {
      trips: Math.max(0, Math.trunc(Number(record.trips) || 0)),
      val: Math.max(0, Number(record.val) || 0),
      companyId: firstNonEmpty(record.companyId) || undefined,
      name: firstNonEmpty(record.name) || undefined,
    };
  });
  return result;
}

function applyCompanyDeltas(
  current: Record<string, CompanyStat>,
  deltas: Map<string, CompanyStat>,
) {
  deltas.forEach((delta, id) => {
    const previous = current[id] || { trips: 0, val: 0 };
    const trips = previous.trips + delta.trips;
    const val = previous.val + delta.val;
    if (trips <= 0) {
      // Company rankings intentionally keep valid companies with zero trips,
      // matching the existing client engine. The current catalog still
      // decides whether that company is visible or has been deleted/moved.
      current[id] = {
        trips: 0,
        val: 0,
        name: previous.name || delta.name,
      };
      return;
    }
    current[id] = {
      trips,
      val: Math.max(0, val),
      name: previous.name || delta.name,
    };
  });
}

function applyDriverDeltas(
  current: Record<string, DriverStat>,
  deltas: Map<string, DriverStat>,
) {
  deltas.forEach((delta, id) => {
    const previous = current[id] || { trips: 0, val: 0 };
    const trips = previous.trips + delta.trips;
    const val = previous.val + delta.val;
    if (trips <= 0) {
      delete current[id];
      return;
    }
    current[id] = {
      trips,
      val: Math.max(0, val),
      companyId: delta.companyId || previous.companyId,
      name: previous.name || delta.name,
    };
  });
}

async function applyAggregateDeltas(deltas: Map<string, AggregateDelta>) {
  if (deltas.size === 0) return;
  const entries = Array.from(deltas.values());

  await db.runTransaction(async (transaction) => {
    const snapshots: FirebaseFirestore.DocumentSnapshot[] = [];
    const references = entries.map((entry) =>
      db.collection(AGGREGATES_COLLECTION).doc(
        aggregateDocumentId(entry.simulatorId, entry.period.key),
      ),
    );

    for (const reference of references) {
      snapshots.push(await transaction.get(reference));
    }

    entries.forEach((entry, index) => {
      const snapshot = snapshots[index];
      if (!snapshot.exists) return;
      const data = snapshot.data() || {};
      if (data.schemaVersion !== SCHEMA_VERSION || data.complete !== true) return;

      const companies = parseCompanyMap(data.companies);
      const drivers = parseDriverMap(data.drivers);
      applyCompanyDeltas(companies, entry.companyDeltas);
      applyDriverDeltas(drivers, entry.driverDeltas);

      transaction.update(references[index], {
        // Update the whole maps so participants whose total reaches zero are
        // actually removed instead of surviving through recursive merge.
        companies,
        drivers,
        sourceTripCount: Math.max(
          0,
          Math.trunc(Number(data.sourceTripCount) || 0) + entry.sourceTripDelta,
        ),
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        revision: admin.firestore.FieldValue.increment(1),
      });
    });
  });
}

export const updateRankingAggregatesOnTripWrite = functions.firestore
  .document("historico_viagens/{tripId}")
  .onWrite(async (change) => {
    const [before, after] = await Promise.all([
      contributionFromData(change.before.exists ? change.before.data() : undefined),
      contributionFromData(change.after.exists ? change.after.data() : undefined),
    ]);

    if (contributionSignature(before) === contributionSignature(after)) return;

    const deltas = new Map<string, AggregateDelta>();
    if (before) addContributionDelta(deltas, before, -1);
    if (after) addContributionDelta(deltas, after, 1);
    await applyAggregateDeltas(deltas);
  });

/**
 * Company status and simulator changes alter the eligible ranking universe.
 * Rebuild only the current UTC week/month, and only when that competitive
 * identity changed. Logo/name edits remain client-resolved and cost no scan.
 */
export const updateRankingAggregatesOnCompanyWrite = functions
  .runWith({ timeoutSeconds: 540, memory: "1GB" })
  .firestore.document("frotas/{companyId}")
  .onWrite(async (change) => {
    const before = change.before.exists ? change.before.data() : undefined;
    const after = change.after.exists ? change.after.data() : undefined;
    const beforeEligible = isEligibleCompany(before);
    const afterEligible = isEligibleCompany(after);

    const catalog = await loadSimulatorCatalog();
    const beforeSimulator = beforeEligible
      ? resolveSimulatorFromRecords({}, before, catalog)
      : "";
    const afterSimulator = afterEligible
      ? resolveSimulatorFromRecords({}, after, catalog)
      : "";

    if (
      beforeEligible === afterEligible &&
      beforeSimulator === afterSimulator
    ) {
      return;
    }

    for (const period of periodsForDate(new Date())) {
      await rebuildPeriod(period);
    }
  });

/**
 * Simulator names and aliases are part of the canonical competitive key.
 * Catalog edits are rare, so rebuilding only the current UTC week/month is a
 * bounded way to prevent an old alias map from keeping companies in the wrong
 * simulator after an administrative correction.
 */
export const updateRankingAggregatesOnSimulatorWrite = functions
  .runWith({ timeoutSeconds: 540, memory: "1GB" })
  .firestore.document("simulators/{simulatorId}")
  .onWrite(async (change) => {
    const before = change.before.exists ? change.before.data() : undefined;
    const after = change.after.exists ? change.after.data() : undefined;
    const signature = (data: FirebaseFirestore.DocumentData | undefined) =>
      [
        data?.name,
        data?.nome,
        data?.label,
        data?.title,
        data?.displayName,
        data?.code,
        data?.slug,
        data?.key,
        data?.simulatorId,
        data?.simuladorId,
        data?.active,
        data?.status,
      ]
        .map((value) => String(value ?? "").trim())
        .join("|");

    if (signature(before) === signature(after)) return;

    simulatorCatalogCache = null;
    for (const period of periodsForDate(new Date())) {
      await rebuildPeriod(period);
    }
  });

async function fetchTripsByDateField(
  field: typeof DATE_FIELDS[number],
  period: PeriodDescriptor,
): Promise<FirebaseFirestore.QueryDocumentSnapshot[]> {
  const documents: FirebaseFirestore.QueryDocumentSnapshot[] = [];
  let cursor: FirebaseFirestore.QueryDocumentSnapshot | null = null;

  while (true) {
    let rangeQuery: FirebaseFirestore.Query = db
      .collection("historico_viagens")
      .where(field, ">=", admin.firestore.Timestamp.fromDate(period.start))
      .where(field, "<=", admin.firestore.Timestamp.fromDate(period.end))
      .orderBy(field, "asc")
      .limit(PAGE_SIZE);
    if (cursor) rangeQuery = rangeQuery.startAfter(cursor);

    const snapshot = await rangeQuery.get();
    documents.push(...snapshot.docs);
    if (snapshot.size < PAGE_SIZE) break;
    cursor = snapshot.docs[snapshot.docs.length - 1];
  }

  return documents;
}

async function fetchPeriodTrips(period: PeriodDescriptor) {
  const results = await Promise.allSettled(
    DATE_FIELDS.map((field) => fetchTripsByDateField(field, period)),
  );
  const fulfilled = results.filter(
    (result): result is PromiseFulfilledResult<FirebaseFirestore.QueryDocumentSnapshot[]> =>
      result.status === "fulfilled",
  );
  if (fulfilled.length === 0) {
    const failure = results.find(
      (result): result is PromiseRejectedResult => result.status === "rejected",
    );
    throw failure?.reason || new Error("Nenhuma consulta de período pôde ser executada.");
  }

  results
    .filter((result): result is PromiseRejectedResult => result.status === "rejected")
    .forEach((result) =>
      console.warn("[RankingAggregates] Alias de data ignorado:", result.reason),
    );

  const byId = new Map<string, FirebaseFirestore.QueryDocumentSnapshot>();
  fulfilled.forEach((result) => {
    result.value.forEach((document) => byId.set(document.id, document));
  });
  return Array.from(byId.values());
}

function isEligibleCompany(data: FirebaseFirestore.DocumentData | undefined): boolean {
  if (!data) return false;
  if (hasUnsupportedSimulatorIdentity(data)) return false;
  const status = normalizeText(data.status || data.situacao || data.state || "active");
  const deleted =
    data.deleted === true ||
    data.softDeleted === true ||
    data.excluida === true ||
    data.excluido === true ||
    ["deleted", "excluida", "excluido", "removed", "removida", "removido"].includes(status);
  return !deleted && ["active", "approved", "ativo"].includes(status);
}

function emptyAggregate(): AggregateData {
  return { companies: {}, drivers: {}, sourceTripCount: 0 };
}

function addToAggregate(
  aggregate: AggregateData,
  contribution: Contribution,
) {
  const company = aggregate.companies[contribution.companyId] || {
    trips: 0,
    val: 0,
    name: contribution.companyName || undefined,
  };
  company.trips += 1;
  company.val += contribution.value;
  if (!company.name && contribution.companyName) company.name = contribution.companyName;
  aggregate.companies[contribution.companyId] = company;

  const driver = aggregate.drivers[contribution.driverId] || {
    trips: 0,
    val: 0,
    companyId: contribution.companyId,
    name: contribution.driverName || undefined,
  };
  driver.trips += 1;
  driver.val += contribution.value;
  driver.companyId = contribution.companyId;
  if (!driver.name && contribution.driverName) driver.name = contribution.driverName;
  aggregate.drivers[contribution.driverId] = driver;
  aggregate.sourceTripCount += 1;
}

async function rebuildPeriod(
  period: PeriodDescriptor,
  onCheckpoint?: (
    stage: RankingCheckpointStage,
    data?: FirebaseFirestore.DocumentData,
  ) => Promise<void>,
) {
  await onCheckpoint?.("collecting", { checkpointPhase: "loading_inputs" });
  const [tripDocuments, companiesSnapshot, catalog] = await Promise.all([
    fetchPeriodTrips(period),
    db.collection("frotas").get(),
    loadSimulatorCatalog(),
  ]);

  const companiesById = new Map<string, FirebaseFirestore.DocumentData>();
  companiesSnapshot.docs.forEach((document) => {
    const data = document.data();
    if (isEligibleCompany(data)) companiesById.set(document.id, data);
  });

  const aggregates = new Map<string, AggregateData>();
  catalog.descriptors.forEach((simulator) => {
    aggregates.set(simulator.id, emptyAggregate());
  });

  // Preserve the previous ranking behavior: valid companies in the selected
  // simulator exist in the company ranking with zero trips until activity is
  // recorded. Hard-deleted companies are deliberately excluded.
  companiesById.forEach((company, companyId) => {
    const simulatorId = resolveSimulatorFromRecords({}, company, catalog);
    if (!simulatorId) return;
    const aggregate = aggregates.get(simulatorId) || emptyAggregate();
    aggregate.companies[companyId] = {
      trips: 0,
      val: 0,
      name: firstNonEmpty(company.companyName, company.name) || undefined,
    };
    aggregates.set(simulatorId, aggregate);
  });

  tripDocuments
    .sort((left, right) => {
      const leftTime = tripMetricDate(left.data())?.getTime() || 0;
      const rightTime = tripMetricDate(right.data())?.getTime() || 0;
      if (leftTime !== rightTime) return leftTime - rightTime;
      return left.id.localeCompare(right.id);
    })
    .forEach((document) => {
    const data = document.data();
    if (!isValidCompletedTrip(data)) return;
    const date = tripMetricDate(data);
    if (!date || date < period.start || date > period.end) return;
    const companyId = companyIdOf(data);
    const driverId = driverIdOf(data);
    if (!companyId || !driverId) return;
    const company = companiesById.get(companyId);
    if (!company) return;
    const simulatorId = resolveSimulatorFromRecords(data, company, catalog);
    if (!simulatorId) return;

    const contribution: Contribution = {
      simulatorId,
      companyId,
      companyName: companyNameOf(data) || firstNonEmpty(company.companyName, company.name),
      driverId,
      driverName: driverNameOf(data),
      value: Math.max(0, parseTripValue(data.valor ?? data.value ?? data.totalValue ?? data.ganho)),
      date,
    };
    const aggregate = aggregates.get(simulatorId) || emptyAggregate();
    addToAggregate(aggregate, contribution);
    aggregates.set(simulatorId, aggregate);
    });

  await onCheckpoint?.("collecting", {
    checkpointPhase: "inputs_loaded",
    sourceDocumentsFetched: tripDocuments.length,
    eligibleCompanies: companiesById.size,
    simulatorCount: aggregates.size,
  });

  const now = admin.firestore.FieldValue.serverTimestamp();
  const writes = Array.from(aggregates.entries()).map(([simulatorId, aggregate]) => ({
    ref: db.collection(AGGREGATES_COLLECTION).doc(
      aggregateDocumentId(simulatorId, period.key),
    ),
    data: {
      schemaVersion: SCHEMA_VERSION,
      simulatorId,
      periodType: period.type,
      periodKey: period.key,
      periodStart: admin.firestore.Timestamp.fromDate(period.start),
      periodEnd: admin.firestore.Timestamp.fromDate(period.end),
      periodTimeZone: "UTC",
      complete: true,
      sourceTripCount: aggregate.sourceTripCount,
      companies: aggregate.companies,
      drivers: aggregate.drivers,
      generatedAt: now,
      updatedAt: now,
      lastReconciledAt: now,
      revision: Date.now(),
    },
  }));

  const totalWriteBatches = Math.ceil(writes.length / WRITE_BATCH_SIZE);
  await onCheckpoint?.("writing", {
    checkpointPhase: "writing_aggregates",
    rebuiltDocuments: writes.length,
    sourceTrips: tripDocuments.length,
    writeBatchesTotal: totalWriteBatches,
    writeBatchesCompleted: 0,
  });

  for (let index = 0; index < writes.length; index += WRITE_BATCH_SIZE) {
    const batch = db.batch();
    const chunk = writes.slice(index, index + WRITE_BATCH_SIZE);
    chunk.forEach((write) => {
      // Full overwrite is intentional: a reconciliation must remove stale
      // participants that no longer belong to the canonical period dataset.
      batch.set(write.ref, write.data);
    });
    await batch.commit();
    await onCheckpoint?.("writing", {
      checkpointPhase: "writing_aggregates",
      rebuiltDocuments: writes.length,
      sourceTrips: tripDocuments.length,
      writeBatchesTotal: totalWriteBatches,
      writeBatchesCompleted: Math.floor(index / WRITE_BATCH_SIZE) + 1,
      lastWrittenAggregateId: chunk[chunk.length - 1]?.ref.id || "",
    });
  }

  return {
    rebuiltDocuments: writes.length,
    sourceTrips: tripDocuments.length,
  };
}

function timestampMs(value: unknown): number {
  const parsed = parseDate(value);
  return parsed ? parsed.getTime() : 0;
}

export const ensureRankingAggregates = functions
  .runWith({ timeoutSeconds: 540, memory: "1GB" })
  .https.onCall(
  async (rawInput: EnsureInput, context): Promise<EnsureResult> => {
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Autenticação obrigatória para carregar o ranking.",
      );
    }

    const simulatorId = firstNonEmpty(rawInput?.simulatorId);
    const periodType = firstNonEmpty(rawInput?.periodType) as PeriodType;
    const periodKey = firstNonEmpty(rawInput?.periodKey);
    if (!simulatorId || !periodKey || !["semana", "mes"].includes(periodType)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Simulador e período válidos são obrigatórios.",
      );
    }

    const period = periodFromKey(periodType, periodKey);
    if (!period) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "A chave do período não é válida.",
      );
    }

    // The public callable is only a bootstrap/reconciliation path for recent
    // rankings. It must never become an authenticated full-history scanner.
    const oldestAllowedStart = Date.now() - 70 * 24 * 60 * 60 * 1000;
    const newestAllowedStart = Date.now() + 24 * 60 * 60 * 1000;
    if (
      period.start.getTime() < oldestAllowedStart ||
      period.start.getTime() > newestAllowedStart
    ) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Somente períodos recentes podem ser consolidados por esta função.",
      );
    }

    const catalog = await loadSimulatorCatalog();
    const canonicalSimulatorId = catalog.ids.has(simulatorId)
      ? simulatorId
      : catalog.byAlias.get(simulatorId) || catalog.byAlias.get(canonicalSimulatorAliasKey(simulatorId)) || "";
    if (!canonicalSimulatorId) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "O simulador selecionado não existe no catálogo.",
      );
    }

    const aggregateRef = db.collection(AGGREGATES_COLLECTION).doc(
      aggregateDocumentId(canonicalSimulatorId, period.key),
    );
    const existing = await aggregateRef.get();
    if (existing.exists) {
      const data = existing.data() || {};
      const reconciledAt = timestampMs(data.lastReconciledAt);
      if (
        data.schemaVersion === SCHEMA_VERSION &&
        data.complete === true &&
        reconciledAt > 0 &&
        Date.now() - reconciledAt <= RECONCILE_AFTER_MS
      ) {
        return { success: true, status: "ready", periodKey: period.key };
      }
    }

    const controlRef = db.collection(CONTROLS_COLLECTION).doc(
      `v${SCHEMA_VERSION}__${period.key}`,
    );
    const runId = safeDocumentId(
      `${period.key}_${context.auth?.uid || "unknown"}_${Date.now()}_${Math.random()}`,
    );
    const lockAcquired = await db.runTransaction(async (transaction) => {
      const control = await transaction.get(controlRef);
      const controlData = control.data() || {};
      const lockedUntil = timestampMs(controlData.lockedUntil);
      if (lockedUntil > Date.now()) return false;

      const failedAt = timestampMs(controlData.failedAt);
      if (
        controlData.checkpointVersion === RANKING_CHECKPOINT_VERSION &&
        controlData.schemaVersion === SCHEMA_VERSION &&
        controlData.status === "failed" &&
        failedAt > 0 &&
        Date.now() - failedAt < FAILED_RETRY_COOLDOWN_MS
      ) {
        return false;
      }

      transaction.set(
        controlRef,
        {
          checkpointVersion: RANKING_CHECKPOINT_VERSION,
          schemaVersion: SCHEMA_VERSION,
          periodType: period.type,
          periodKey: period.key,
          periodStart: admin.firestore.Timestamp.fromDate(period.start),
          periodEnd: admin.firestore.Timestamp.fromDate(period.end),
          periodTimeZone: "UTC",
          status: "running",
          checkpointStage: "collecting",
          checkpointPhase: "lock_acquired",
          runId,
          lockedUntil: admin.firestore.Timestamp.fromMillis(Date.now() + LOCK_TTL_MS),
          startedAt: admin.firestore.FieldValue.serverTimestamp(),
          lastCheckpointAt: admin.firestore.FieldValue.serverTimestamp(),
          requestedBy: context.auth?.uid || "unknown",
          error: admin.firestore.FieldValue.delete(),
        },
        { merge: true },
      );
      return true;
    });

    if (!lockAcquired) {
      return { success: true, status: "in_progress", periodKey: period.key };
    }

    let checkpointStage: RankingCheckpointStage = "collecting";
    const saveCheckpoint = async (
      stage: RankingCheckpointStage,
      data: FirebaseFirestore.DocumentData = {},
    ) => {
      checkpointStage = stage;
      await controlRef.set(
        {
          checkpointVersion: RANKING_CHECKPOINT_VERSION,
          schemaVersion: SCHEMA_VERSION,
          status: stage === "completed" ? "completed" : "running",
          checkpointStage: stage,
          runId,
          lockedUntil: stage === "completed"
            ? admin.firestore.Timestamp.fromMillis(0)
            : admin.firestore.Timestamp.fromMillis(Date.now() + LOCK_TTL_MS),
          lastCheckpointAt: admin.firestore.FieldValue.serverTimestamp(),
          ...data,
        },
        { merge: true },
      );
    };

    try {
      const result = await rebuildPeriod(period, saveCheckpoint);
      await saveCheckpoint("completed", {
        checkpointPhase: "completed",
        completedAt: admin.firestore.FieldValue.serverTimestamp(),
        rebuiltDocuments: result.rebuiltDocuments,
        sourceTrips: result.sourceTrips,
        error: admin.firestore.FieldValue.delete(),
      });
      return {
        success: true,
        status: "rebuilt",
        periodKey: period.key,
        ...result,
      };
    } catch (error) {
      await controlRef.set(
        {
          checkpointVersion: RANKING_CHECKPOINT_VERSION,
          schemaVersion: SCHEMA_VERSION,
          status: "failed",
          checkpointStage,
          runId,
          lockedUntil: admin.firestore.Timestamp.fromMillis(0),
          failedAt: admin.firestore.FieldValue.serverTimestamp(),
          lastCheckpointAt: admin.firestore.FieldValue.serverTimestamp(),
          error: String(error instanceof Error ? error.message : error).slice(0, 500),
        },
        { merge: true },
      );
      console.error("[RankingAggregates] Falha ao reconstruir período:", error);
      throw new functions.https.HttpsError(
        "internal",
        "Não foi possível consolidar o ranking agora.",
      );
    }
  },
);
