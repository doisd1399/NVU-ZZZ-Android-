import { normalizeSimulatorId, resolveSimulatorId } from "./resolveSimulator";

export type SimulatorResolutionSource =
  | "explicit-id"
  | "explicit-key"
  | "explicit-name"
  | "contract-id"
  | "contract-name"
  | "company-single"
  | "unknown";

export type SimulatorResolutionConfidence = "explicit" | "catalog" | "fallback" | "unknown";

export interface SimulatorResolution {
  simulatorId: string;
  source: SimulatorResolutionSource;
  confidence: SimulatorResolutionConfidence;
}

type AnyRecord = Record<string, unknown>;

const text = (value: unknown): string =>
  typeof value === "string" ? value.trim() : String(value || "").trim();

const findCatalogId = (
  value: unknown,
  simulators: AnyRecord[] = [],
): string => {
  const raw = text(value);
  if (!raw) return "";
  const normalized = normalizeSimulatorId(raw);
  const match = simulators.find((item) =>
    [item.id, item.name, item.code, item.slug, item.key, item.simulatorId]
      .map(normalizeSimulatorId)
      .filter(Boolean)
      .includes(normalized),
  );
  return text(match?.id);
};

/**
 * Resolves a simulator without inventing an association. Explicit identifiers
 * always win; textual legacy values are accepted only when they match the
 * authoritative catalog exactly.
 */
export function resolveEntitySimulator(
  entity: AnyRecord | null | undefined,
  options: {
    simulators?: AnyRecord[];
    company?: AnyRecord | null;
    contract?: AnyRecord | null;
  } = {},
): SimulatorResolution {
  const source = entity || {};
  const simulators = Array.isArray(options.simulators) ? options.simulators : [];

  for (const key of ["simulatorId", "simuladorId", "simulator_id"]) {
    const id = findCatalogId(source[key], simulators) || text(source[key]);
    if (id) return { simulatorId: id, source: "explicit-id", confidence: "explicit" };
  }
  for (const key of ["simulatorKey", "simuladorKey", "simulatorCode", "simuladorCode"]) {
    const id = findCatalogId(source[key], simulators);
    if (id) return { simulatorId: id, source: "explicit-key", confidence: "catalog" };
  }
  for (const key of ["simulatorName", "simuladorNome", "simulator", "simulador"]) {
    const id = findCatalogId(source[key], simulators);
    if (id) return { simulatorId: id, source: "explicit-name", confidence: "catalog" };
  }

  const contract = options.contract;
  if (contract) {
    const resolved = resolveEntitySimulator(contract, { simulators });
    if (resolved.simulatorId) {
      return {
        ...resolved,
        source: resolved.source === "explicit-id" ? "contract-id" : "contract-name",
      };
    }
  }

  const company = options.company;
  const companyIds = Array.isArray(company?.simulatorIds)
    ? company.simulatorIds.map(text).filter(Boolean)
    : [];
  const legacyCompanyId = text(company?.simulatorId || company?.simuladorId);
  const available = Array.from(new Set([...companyIds, legacyCompanyId].filter(Boolean)));
  if (available.length === 1) {
    return { simulatorId: available[0], source: "company-single", confidence: "fallback" };
  }

  return { simulatorId: "", source: "unknown", confidence: "unknown" };
}

export function getCompanySimulatorIds(
  company: AnyRecord | null | undefined,
  simulators: AnyRecord[] = [],
): string[] {
  const raw = [
    ...(Array.isArray(company?.simulatorIds) ? company.simulatorIds : []),
    company?.simulatorId,
    company?.simuladorId,
  ]
    .map(text)
    .filter(Boolean);
  return Array.from(new Set(raw.map((value) => findCatalogId(value, simulators) || value)));
}

export function chooseValidCompanySimulator(
  company: AnyRecord | null | undefined,
  simulators: AnyRecord[] = [],
  preferredId?: string | null,
): string | null {
  const ids = getCompanySimulatorIds(company, simulators);
  const preferred = text(preferredId || company?.defaultSimulatorId);
  if (preferred && ids.includes(preferred)) return preferred;
  return ids.length === 1 ? ids[0] : ids[0] || null;
}

export function isSimulatorRecordInScope(
  entity: AnyRecord,
  simulatorId: string | null | undefined,
  options: { simulators?: AnyRecord[]; company?: AnyRecord | null; contract?: AnyRecord | null } = {},
): boolean {
  if (!simulatorId) return false;
  const resolved = resolveEntitySimulator(entity, options);
  return resolved.simulatorId === simulatorId;
}

export function persistSimulatorSelection(uid: string | null | undefined, companyId: string | null | undefined, simulatorId: string | null | undefined) {
  if (!uid || !companyId) return;
  const key = `nvu.session.v6.simulator.${uid}.${companyId}`;
  try {
    if (simulatorId) localStorage.setItem(key, simulatorId);
    else localStorage.removeItem(key);
  } catch {
    // Storage is best effort; React state remains authoritative in-session.
  }
}

export function readSimulatorSelection(uid: string | null | undefined, companyId: string | null | undefined): string | null {
  if (!uid || !companyId) return null;
  try {
    return localStorage.getItem(`nvu.session.v6.simulator.${uid}.${companyId}`);
  } catch {
    return null;
  }
}

export function normalizeSimulatorIds(values: unknown, simulators: AnyRecord[] = []): string[] {
  const list = Array.isArray(values) ? values : [values];
  return Array.from(new Set(list.map(text).filter(Boolean).map((value) => findCatalogId(value, simulators) || value)));
}

export const simulatorIdentityKey = (value: unknown): string => normalizeSimulatorId(text(value));
