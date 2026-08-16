import {
  collection,
  query,
  where,
  onSnapshot,
  getDocs,
  getDocsFromServer,
  getDoc,
  doc,
  updateDoc,
  deleteDoc,
  addDoc,
  Timestamp
} from "firebase/firestore";
import { db } from "../lib/firebase";
import {
  isAuthTeardownActive,
  onAuthTeardown,
} from "../lib/authLifecycle";
import { normalizeTrip } from "../lib/tripNormalizer";
import { areTripSourcesReady, mergeTripSources } from "../lib/tripDataset";
import { isOpenJobStatus } from "../lib/jobStatus";



type TripDocument = Record<string, any> & { id: string };

const companyLegacyTripsCache = new Map<string, TripDocument[]>();
const companyLegacyTripsPromises = new Map<string, Promise<TripDocument[]>>();
const driverLegacyTripsCache = new Map<string, TripDocument[]>();
const driverLegacyTripsPromises = new Map<string, Promise<TripDocument[]>>();
const rangeLegacyTripsCache = new Map<string, TripDocument[]>();
const rangeLegacyTripsPromises = new Map<string, Promise<TripDocument[]>>();
let compatibilityTeardownAttached = false;

const COMPANY_ID_FIELDS = ["companyId", "empresaId", "company_id", "empresa_id"] as const;
const DRIVER_ID_FIELDS = ["driverId", "motoristaId", "motorista_id", "userId", "driver_id"] as const;
const LEGACY_DATE_FIELDS = ["dataFechamento", "date", "dataLancamento", "createdAt"] as const;

const clearCompatibilityCaches = () => {
  companyLegacyTripsCache.clear();
  companyLegacyTripsPromises.clear();
  driverLegacyTripsCache.clear();
  driverLegacyTripsPromises.clear();
  rangeLegacyTripsCache.clear();
  rangeLegacyTripsPromises.clear();
};

const ensureCompatibilityTeardown = () => {
  if (compatibilityTeardownAttached || typeof window === "undefined") return;
  compatibilityTeardownAttached = true;
  onAuthTeardown(clearCompatibilityCaches);
};

const documentCache = new WeakMap<any, TripDocument>();
const mapSnapshotDocuments = (snapshot: any): TripDocument[] =>
  snapshot.docs.map((document: any) => {
    if (documentCache.has(document)) {
      return documentCache.get(document);
    }
    const data = { id: document.id, ...document.data() };
    documentCache.set(document, data);
    return data;
  });

/**
 * Firestore may deliver a locally cached snapshot before the server result.
 * A non-empty cached snapshot is safe to paint immediately and is then
 * reconciled by the authoritative server snapshot. Empty cached snapshots are
 * withheld until the server confirms them, preventing a false "sem dados"
 * state while the network request is still in flight.
 */
const isAuthoritativeSnapshot = (snapshot: any) =>
  snapshot?.metadata?.fromCache !== true &&
  snapshot?.metadata?.hasPendingWrites !== true;

const isDisplayableSnapshot = (snapshot: any) =>
  snapshot?.metadata?.hasPendingWrites !== true &&
  (snapshot?.metadata?.fromCache !== true || snapshot?.docs?.length > 0);

/**
 * Legacy documents can predate the canonical `completedAt` field. They are
 * loaded once per authenticated session, then merged into every bounded
 * realtime query. New writes always include `completedAt`, so the expensive
 * compatibility scan is never kept as a live collection-wide listener.
 *
 * This cache is intentionally memory-only. A browser/WebView must not persist
 * an "empty" result for several days: legacy documents can be added by a
 * different client at any time and every viewport must resolve the same
 * server-backed dataset.
 */
const mergeSnapshots = (snapshots: any[]): TripDocument[] => {
  const merged = new Map<string, TripDocument>();
  snapshots.forEach((snapshot) => {
    mapSnapshotDocuments(snapshot).forEach((trip) => merged.set(trip.id, trip));
  });
  return Array.from(merged.values());
};

const loadIdentityTripsOnce = async (
  cacheKey: string,
  fields: readonly string[],
  cache: Map<string, TripDocument[]>,
  promises: Map<string, Promise<TripDocument[]>>,
): Promise<TripDocument[]> => {
  const cached = cache.get(cacheKey);
  if (cached) return cached;

  const inFlight = promises.get(cacheKey);
  if (inFlight) return inFlight;

  // Identity history must be just as deterministic as ranking history. Every
  // legacy alias is a bounded server query for the same identity; accepting
  // only the fulfilled aliases would silently turn a transient failure into a
  // "complete" in-memory cache for the rest of the session. Wait for every
  // alias request to settle, but commit the dataset only when all succeeded.
  // Waiting for all requests also avoids overlapping still-running reads when
  // the hook schedules a retry after a transient failure.
  const promise = Promise.allSettled(
    fields.map((field) =>
      getDocsFromServer(
        query(
          collection(db, "historico_viagens"),
          where(field, "==", cacheKey),
        ),
      ),
    ),
  )
    .then((results) => {
      const snapshots = results.map((result) => {
        if (result.status === "rejected") throw result.reason;
        return result.value;
      });
      return mergeSnapshots(snapshots);
    })
    .then((trips) => {
      if (!isAuthTeardownActive()) cache.set(cacheKey, trips);
      return trips;
    })
    .finally(() => {
      promises.delete(cacheKey);
    });

  promises.set(cacheKey, promise);
  return promise;
};

/**
 * Loads every historical identity alias once per authenticated session.
 * Canonical documents are intentionally allowed in the result: the merge by
 * Firestore id removes duplicates, while alias-only documents remain visible.
 */
const loadLegacyTripsOnce = async (filter?: {
  companyId?: string;
  driverId?: string;
}): Promise<TripDocument[]> => {
  ensureCompatibilityTeardown();

  if (!filter || (!filter.companyId && !filter.driverId)) {
    if (import.meta.env.DEV) {
      console.warn(
        "[TripsRepository] Leitura legada sem companyId/driverId bloqueada.",
      );
    }
    return [];
  }

  if (filter.companyId) {
    return loadIdentityTripsOnce(
      filter.companyId,
      COMPANY_ID_FIELDS,
      companyLegacyTripsCache,
      companyLegacyTripsPromises,
    );
  }

  return loadIdentityTripsOnce(
    filter.driverId as string,
    DRIVER_ID_FIELDS,
    driverLegacyTripsCache,
    driverLegacyTripsPromises,
  );
};

const rangeCacheKey = (startDate: Date, endDate: Date) =>
  `${startDate.getTime()}:${endDate.getTime()}`;

/**
 * Loads legacy trips through bounded date-range queries. This replaces the
 * former collection-wide compatibility scan and keeps Ranking Global aligned
 * with NVU News without restoring an unbounded client read.
 */
const loadLegacyTripsByDateRangeOnce = async (
  startDate: Date,
  endDate: Date,
): Promise<TripDocument[]> => {
  ensureCompatibilityTeardown();
  const key = rangeCacheKey(startDate, endDate);
  const cached = rangeLegacyTripsCache.get(key);
  if (cached) return cached;

  const inFlight = rangeLegacyTripsPromises.get(key);
  if (inFlight) return inFlight;

  const lowerBound = Timestamp.fromDate(startDate);
  const upperBound = Timestamp.fromDate(endDate);
  // Ranking parity requires the whole legacy date-alias set to be confirmed
  // by the server before it can be reused. `Promise.allSettled` previously
  // allowed one successful alias query to turn a partial Android result into
  // the in-memory range cache for the rest of the session. A transient failure
  // can therefore never be promoted to a complete ranking dataset now.
  const promise = Promise.all(
    LEGACY_DATE_FIELDS.map((field) =>
      getDocsFromServer(
        query(
          collection(db, "historico_viagens"),
          where(field, ">=", lowerBound),
          where(field, "<=", upperBound),
        ),
      ),
    ),
  )
    .then((snapshots) => mergeSnapshots(snapshots))
    .then((trips) =>
      trips.filter((trip) => isTripInsideRange(trip, startDate, endDate)),
    )
    .then((trips) => {
      if (!isAuthTeardownActive()) rangeLegacyTripsCache.set(key, trips);
      return trips;
    })
    .finally(() => {
      rangeLegacyTripsPromises.delete(key);
    });

  rangeLegacyTripsPromises.set(key, promise);
  return promise;
};

const isTripInsideRange = (trip: TripDocument, startDate: Date, endDate: Date) => {
  const metricDate = normalizeTrip(trip as any).metricDate;
  return metricDate >= startDate && metricDate <= endDate;
};

const backfillInFlight = new Map<string, Promise<void>>();
const backfillCompleted = new Set<string>();

export class TripsRepository {
  static listenCompanyTrips(
    companyId: string,
    onNext: (trips: any[]) => void,
    onError?: (err: any) => void,
  ) {
    if (!companyId) {
      onNext([]);
      return () => {};
    }

    // New trip documents always write `companyId`. Keep one realtime listener
    // on that canonical field and resolve old empresaId/company_id aliases only
    // once per session. This removes two permanent duplicate listeners without
    // dropping historical records.
    let active = true;
    let canonicalAuthoritative = false;
    let legacyReady = false;
    let canonicalTrips: TripDocument[] = [];
    let legacyTrips: TripDocument[] = [];

    const emit = () => {
      if (!active || isAuthTeardownActive()) return;

      // Never publish a partial source. Desktop browsers and Android WebViews
      // resolve the canonical listener and the legacy compatibility reads at
      // different speeds; publishing the canonical subset first made each
      // viewport capture a different ranking/history snapshot. The first
      // result is now committed only after the authoritative server snapshot
      // and the bounded legacy scan have both settled.
      if (!areTripSourcesReady(canonicalAuthoritative, legacyReady)) return;
      onNext(mergeTripSources(canonicalTrips, legacyTrips));
    };

    void loadLegacyTripsOnce({ companyId })
      .then((trips) => {
        if (!active || isAuthTeardownActive()) return;
        legacyTrips = trips;
        legacyReady = true;
        emit();
      })
      .catch((error) => {
        if (!active || isAuthTeardownActive()) return;
        console.warn("Falha ao carregar viagens legadas da empresa:", error);
        // Do not promote a failed compatibility read to an authoritative empty
        // source. The hook keeps the last valid visible dataset and retries
        // these filtered identity queries with backoff.
        onError?.(error);
      });

    const unsubscribe = onSnapshot(
      query(
        collection(db, "historico_viagens"),
        where("companyId", "==", companyId),
      ),
      { includeMetadataChanges: true },
      (snapshot) => {
        if (
          !active ||
          isAuthTeardownActive() ||
          !isDisplayableSnapshot(snapshot)
        )
          return;
        canonicalTrips = mapSnapshotDocuments(snapshot);
        canonicalAuthoritative = isAuthoritativeSnapshot(snapshot);
        emit();
      },
      (error) => {
        if (!active || isAuthTeardownActive()) return;
        onError?.(error);
      },
    );

    return () => {
      active = false;
      try {
        unsubscribe();
      } catch {
        // Cleanup is best-effort.
      }
    };
  }

  static listenDriverTrips(
    driverId: string,
    onNext: (trips: any[]) => void,
    onError?: (err: any) => void,
  ) {
    if (!driverId) {
      onNext([]);
      return () => {};
    }

    let active = true;
    let canonicalAuthoritative = false;
    let legacyReady = false;
    let canonicalTrips: TripDocument[] = [];
    let legacyTrips: TripDocument[] = [];

    const emit = () => {
      if (!active || isAuthTeardownActive()) return;

      if (!areTripSourcesReady(canonicalAuthoritative, legacyReady)) return;
      onNext(mergeTripSources(canonicalTrips, legacyTrips));
    };

    void loadLegacyTripsOnce({ driverId })
      .then((trips) => {
        if (!active || isAuthTeardownActive()) return;
        legacyTrips = trips;
        legacyReady = true;
        emit();
      })
      .catch((error) => {
        if (!active || isAuthTeardownActive()) return;
        console.warn("Falha ao carregar viagens legadas do motorista:", error);
        // A failed legacy alias query is unknown, not an empty history. Keep
        // the previously painted cache intact and delegate retry to the hook.
        onError?.(error);
      });

    const unsubscribe = onSnapshot(
      query(
        collection(db, "historico_viagens"),
        where("driverId", "==", driverId),
      ),
      { includeMetadataChanges: true },
      (snapshot) => {
        if (
          !active ||
          isAuthTeardownActive() ||
          !isDisplayableSnapshot(snapshot)
        )
          return;
        canonicalTrips = mapSnapshotDocuments(snapshot);
        canonicalAuthoritative = isAuthoritativeSnapshot(snapshot);
        emit();
      },
      (error) => {
        if (!active || isAuthTeardownActive()) return;
        onError?.(error);
      },
    );

    return () => {
      active = false;
      try {
        unsubscribe();
      } catch {
        // Cleanup is best-effort.
      }
    };
  }

  /**
   * Realtime source for ranking/performance screens. Canonical completedAt
   * records remain live, while bounded server reads resolve the historical
   * date aliases and merge them atomically by document id.
   *
   * Every query uses a single range field and therefore relies on Firestore's
   * automatic single-field indexes; no composite index is required.
   */
  static listenTripsByDateRange(
    startDate: Date,
    endDate: Date,
    onNext: (trips: any[]) => void,
    onError?: (err: any) => void,
  ) {
    const safeStart = Number.isNaN(startDate.getTime())
      ? new Date(0)
      : startDate;
    const safeEnd = Number.isNaN(endDate.getTime()) ? new Date() : endDate;

    let active = true;
    let canonicalAuthoritative = false;
    let legacyReady = false;
    let canonicalTrips: TripDocument[] = [];
    let legacyTrips: TripDocument[] = [];

    const emit = () => {
      if (!active || isAuthTeardownActive()) return;

      // Both inputs are required before committing a result. This keeps the
      // ranking source identical on desktop and Android, where cache/listener
      // timing is not the same.
      if (!areTripSourcesReady(canonicalAuthoritative, legacyReady)) return;
      onNext(mergeTripSources(canonicalTrips, legacyTrips));
    };

    void loadLegacyTripsByDateRangeOnce(safeStart, safeEnd)
      .then((trips) => {
        if (!active || isAuthTeardownActive()) return;
        legacyTrips = trips;
        legacyReady = true;
        emit();
      })
      .catch((error) => {
        if (!active || isAuthTeardownActive()) return;
        console.warn("Falha ao confirmar compatibilidade de viagens:", error);
        // Do not mark an empty/partial legacy source as ready. Propagate the
        // failure so useTripsRealtime closes this generation and retries the
        // same bounded server reads with its existing exponential backoff.
        onError?.(error);
      });

    const rangeQuery = query(
      collection(db, "historico_viagens"),
      where("completedAt", ">=", Timestamp.fromDate(safeStart)),
      where("completedAt", "<=", Timestamp.fromDate(safeEnd)),
    );

    const unsubscribe = onSnapshot(
      rangeQuery,
      { includeMetadataChanges: true },
      (snapshot) => {
        if (
          !active ||
          isAuthTeardownActive() ||
          !isDisplayableSnapshot(snapshot)
        )
          return;
        canonicalTrips = mapSnapshotDocuments(snapshot);
        canonicalAuthoritative = isAuthoritativeSnapshot(snapshot);
        emit();
      },
      (error) => {
        if (!active || isAuthTeardownActive()) return;
        onError?.(error);
      },
    );

    return () => {
      active = false;
      try {
        unsubscribe();
      } catch {
        // Cleanup is best-effort.
      }
    };
  }


  static async getCompanyTrips(companyId: string) {
    if (!companyId) return [];

    const [canonicalSnapshot, legacyTrips] = await Promise.all([
      getDocsFromServer(
        query(
          collection(db, "historico_viagens"),
          where("companyId", "==", companyId),
        ),
      ),
      loadLegacyTripsOnce({ companyId }),
    ]);

    return mergeTripSources(mapSnapshotDocuments(canonicalSnapshot), legacyTrips);
  }

  static async addTrip(data: any) {
    return await addDoc(collection(db, "historico_viagens"), data);
  }

  static async updateTrip(tripId: string, data: any) {
    return await updateDoc(doc(db, "historico_viagens", tripId), data);
  }

  /**
   * Recalculates a job's progress from valid trip documents instead of
   * incrementing/decrementing a potentially stale counter.
   */
  static async syncJobProgress(jobId: string): Promise<number> {
    if (!jobId) return 0;

    const [jobSnapshot, tripsSnapshot] = await Promise.all([
      getDoc(doc(db, "trabalhos", jobId)),
      getDocsFromServer(
        query(
          collection(db, "historico_viagens"),
          where("jobId", "==", jobId),
        ),
      ),
    ]);

    const realProgress = tripsSnapshot.docs
      .map((tripDoc) =>
        normalizeTrip({ id: tripDoc.id, ...tripDoc.data() } as any),
      )
      .filter((trip) => trip.isValid).length;

    if (!jobSnapshot.exists()) return realProgress;

    const jobData = jobSnapshot.data();
    const updates: Record<string, any> = { progress: realProgress };
    const contractId = jobData.contractId;

    if (contractId) {
      const contractSnapshot = await getDoc(doc(db, "contratos", contractId));
      if (contractSnapshot.exists()) {
        const totalDeliveries = Number(
          contractSnapshot.data()?.totalDeliveries || 0,
        );
        const status = String(jobData.status || "");

        if (
          totalDeliveries > 0 &&
          realProgress >= totalDeliveries &&
          isOpenJobStatus(status)
        ) {
          updates.status = "awaiting_completion";
        } else if (status === "awaiting_completion" && realProgress < totalDeliveries) {
          updates.status = "active";
        } else if (status === "pending" && realProgress > 0) {
          updates.status = "active";
        }
      }
    }

    await updateDoc(jobSnapshot.ref, updates);
    return realProgress;
  }

  static async deleteTrip(tripId: string) {
    return await deleteDoc(doc(db, "historico_viagens", tripId));
  }

  static async runBackfill(activeCompanyId: string) {
    // This is a one-time migration for legacy trip records. Share the same
    // promise between mounted screens and skip it after the company is done.
    if (
      !activeCompanyId ||
      isAuthTeardownActive() ||
      backfillCompleted.has(activeCompanyId)
    )
      return;

    const currentBackfill = backfillInFlight.get(activeCompanyId);
    if (currentBackfill) {
      await currentBackfill;
      return;
    }

    const backfillPromise = (async () => {
      if (isAuthTeardownActive()) return;
      const qTrips = query(
        collection(db, "historico_viagens"),
        where("empresaId", "==", activeCompanyId),
      );
      const tripsSnap = await getDocsFromServer(qTrips);
      if (isAuthTeardownActive()) return;

      let needsMigration = false;
      for (const docSnap of tripsSnap.docs) {
        const t = docSnap.data();
        if (
          !t.veiculoNome ||
          t.veiculoNome === "-" ||
          !t.contratoNumero ||
          t.contratoNumero === "-"
        ) {
          needsMigration = true;
          break;
        }
      }

      if (!needsMigration) return;

      const [vSnap, cSnap, tSnap] = await Promise.all([
        getDocsFromServer(
          query(
            collection(db, "vehicles"),
            where("companyId", "==", activeCompanyId),
          ),
        ),
        getDocsFromServer(
          query(
            collection(db, "contracts"),
            where("companyId", "==", activeCompanyId),
          ),
        ),
        getDocsFromServer(
          query(
            collection(db, "trailers"),
            where("companyId", "==", activeCompanyId),
          ),
        ),
      ]);
      if (isAuthTeardownActive()) return;

      const vMap = new Map(vSnap.docs.map((d) => [d.id, d.data()]));
      const cMap = new Map(cSnap.docs.map((d) => [d.id, d.data()]));
      const tMap = new Map(tSnap.docs.map((d) => [d.id, d.data()]));

      const updates = tripsSnap.docs.map(async (docSnap) => {
        const tData = docSnap.data();
        if (
          !tData.veiculoNome ||
          tData.veiculoNome === "-" ||
          !tData.contratoNumero ||
          tData.contratoNumero === "-"
        ) {
          const v = vMap.get(tData.veiculoId);
          const c = cMap.get(tData.contratoId);
          const t = tMap.get(tData.reboqueId);

          const veiculoNome = v
            ? `${v.name || ""}`.trim()
            : "Veículo não encontrado";
          const veiculoPlaca = v?.plate || "";
          const contratoNumero = c ? c.name : "Contrato não encontrado";
          const contratoDescricao = "";
          const reboqueNome = t
            ? `${t.name || ""}`.trim()
            : "Reboque não encontrado";

          if (isAuthTeardownActive()) return;
          await updateDoc(doc(db, "historico_viagens", docSnap.id), {
            veiculoNome,
            veiculoPlaca,
            contratoNumero,
            contratoDescricao,
            reboqueNome,
          });
        }
      });

      await Promise.all(updates);
    })()
      .then(() => {
        if (!isAuthTeardownActive()) {
          backfillCompleted.add(activeCompanyId);
        }
      })
      .catch((error) => {
        if (!isAuthTeardownActive()) {
          console.warn("Backfill failed:", error);
        }
      });

    backfillInFlight.set(activeCompanyId, backfillPromise);
    try {
      await backfillPromise;
    } finally {
      if (backfillInFlight.get(activeCompanyId) === backfillPromise) {
        backfillInFlight.delete(activeCompanyId);
      }
    }
  }

  static async checkImageHash(hash: string): Promise<boolean> {
    const q = query(
      collection(db, "historico_viagens"),
      where("imageHash", "==", hash)
    );
    const snap = await getDocs(q);
    return !snap.empty;
  }
}
