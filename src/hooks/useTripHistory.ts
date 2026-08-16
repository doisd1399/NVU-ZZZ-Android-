import { useLayoutEffect, useState } from "react";
import { TripsRepository } from "../repositories/TripsRepository";
import { getTripMetricDate } from "../lib/tripNormalizer";
import {
  isAuthTeardownActive,
  onAuthTeardown,
} from "../lib/authLifecycle";

type TripHistoryState = {
  trips: any[];
  loading: boolean;
  error: any;
};

type TripHistoryCacheEntry = TripHistoryState & {
  listeners: Set<() => void>;
  unsubscribe: (() => void) | null;
  releaseTimer: ReturnType<typeof setTimeout> | null;
  retryTimer: ReturnType<typeof setTimeout> | null;
  retryAttempt: number;
};

const CACHE_RELEASE_DELAY_MS = 120_000;
const RETRY_MAX_DELAY_MS = 30_000;
const PERSISTED_CACHE_MAX_AGE_MS = 10 * 60 * 1000;
const PERSISTED_TRIP_LIMIT = 240;
const PERSISTED_CACHE_PREFIX = "nvu.instant.v1.company-trips.";
const tripHistoryCache = new Map<string, TripHistoryCacheEntry>();

const EMPTY_STATE: TripHistoryState = {
  trips: [],
  loading: false,
  error: null,
};

let teardownListenerAttached = false;

const stripHeavyTripFields = (trip: any) => {
  const compact: Record<string, unknown> = {};
  Object.entries(trip || {}).forEach(([key, value]) => {
    if (typeof value === "string" && value.length > 40_000) return;
    compact[key] = value;
  });
  return compact;
};

const persistedCacheKey = (companyId: string) =>
  `${PERSISTED_CACHE_PREFIX}${encodeURIComponent(companyId)}`;

const readPersistedTrips = (companyId: string): any[] => {
  try {
    const raw = sessionStorage.getItem(persistedCacheKey(companyId));
    if (!raw) return [];
    const parsed = JSON.parse(raw) as { trips?: any[]; cachedAt?: number };
    if (
      !Array.isArray(parsed.trips) ||
      typeof parsed.cachedAt !== "number" ||
      Date.now() - parsed.cachedAt > PERSISTED_CACHE_MAX_AGE_MS
    ) {
      sessionStorage.removeItem(persistedCacheKey(companyId));
      return [];
    }
    return parsed.trips;
  } catch {
    return [];
  }
};

const writePersistedTrips = (companyId: string, trips: any[]) => {
  try {
    const compactTrips = [...trips]
      .sort(
        (a, b) =>
          getTripMetricDate(b).getTime() - getTripMetricDate(a).getTime(),
      )
      .slice(0, PERSISTED_TRIP_LIMIT)
      .map(stripHeavyTripFields);
    sessionStorage.setItem(
      persistedCacheKey(companyId),
      JSON.stringify({ trips: compactTrips, cachedAt: Date.now() }),
    );
  } catch {
    // Persistent cache is only an acceleration layer.
  }
};

function ensureTeardownListener() {
  if (teardownListenerAttached || typeof window === "undefined") return;
  teardownListenerAttached = true;
  onAuthTeardown(() => {
    for (const entry of tripHistoryCache.values()) {
      if (entry.releaseTimer) clearTimeout(entry.releaseTimer);
      if (entry.retryTimer) clearTimeout(entry.retryTimer);
      entry.releaseTimer = null;
      entry.retryTimer = null;
      entry.retryAttempt = 0;
      try {
        entry.unsubscribe?.();
      } catch {
        // A listener cleanup must never abort the global logout signal.
      }
      entry.unsubscribe = null;
      entry.trips = [];
      entry.loading = false;
      entry.error = null;
      notify(entry);
      entry.listeners.clear();
    }
    tripHistoryCache.clear();
  });
}

function notify(entry: TripHistoryCacheEntry) {
  entry.listeners.forEach((listener) => listener());
}

function scheduleEntryRelease(companyId: string, entry: TripHistoryCacheEntry) {
  if (isAuthTeardownActive()) return;
  if (entry.releaseTimer) clearTimeout(entry.releaseTimer);

  entry.releaseTimer = setTimeout(() => {
    entry.releaseTimer = null;

    // A component may have mounted again while the release timer was pending.
    if (entry.listeners.size > 0) return;

    try {
      entry.unsubscribe?.();
    } catch {
      // Listener cleanup is best-effort.
    }
    entry.unsubscribe = null;
    if (entry.retryTimer) clearTimeout(entry.retryTimer);
    entry.retryTimer = null;
    entry.retryAttempt = 0;

    if (tripHistoryCache.get(companyId) === entry) {
      tripHistoryCache.delete(companyId);
    }
  }, CACHE_RELEASE_DELAY_MS);
}

function scheduleHistoryRetry(
  companyId: string,
  entry: TripHistoryCacheEntry,
) {
  if (isAuthTeardownActive() || entry.retryTimer) return;

  const delay = Math.min(
    RETRY_MAX_DELAY_MS,
    1_000 * 2 ** entry.retryAttempt,
  );
  entry.retryAttempt += 1;
  entry.retryTimer = setTimeout(() => {
    entry.retryTimer = null;
    if (
      isAuthTeardownActive() ||
      entry.listeners.size === 0 ||
      tripHistoryCache.get(companyId) !== entry
    ) {
      return;
    }
    try {
      entry.unsubscribe?.();
    } catch {
      // Listener cleanup is best-effort before a retry.
    }
    entry.unsubscribe = null;
    entry.loading = entry.trips.length === 0;
    subscribeEntry(companyId, entry);
  }, delay);
}

function subscribeEntry(companyId: string, entry: TripHistoryCacheEntry) {
  if (isAuthTeardownActive()) return;

  try {
    entry.unsubscribe = TripsRepository.listenCompanyTrips(
      companyId,
      (trips) => {
        if (isAuthTeardownActive()) return;
        entry.retryAttempt = 0;
        if (entry.retryTimer) clearTimeout(entry.retryTimer);
        entry.retryTimer = null;
        entry.trips = trips;
        entry.loading = false;
        entry.error = null;
        writePersistedTrips(companyId, trips);
        notify(entry);
      },
      (error) => {
        if (isAuthTeardownActive()) return;
        console.warn("Error fetching trip history:", error);
        try {
          entry.unsubscribe?.();
        } catch {
          // Firestore may already have closed the failed listener.
        }
        entry.unsubscribe = null;
        entry.error = error;
        // Keep the latest visible dataset during a retry. The repository may
        // publish canonical data first and reconcile legacy aliases afterward.
        entry.loading = entry.trips.length === 0;
        notify(entry);

        scheduleHistoryRetry(companyId, entry);
      },
    );
  } catch (error) {
    if (isAuthTeardownActive()) return;
    console.warn("Error subscribing to trip history:", error);
    entry.error = error;
    entry.loading = entry.trips.length === 0;
    notify(entry);
    // Synchronous listener-attachment failures must follow the same bounded
    // recovery path as asynchronous Firestore listener errors.
    scheduleHistoryRetry(companyId, entry);
  }
}

function ensureEntry(companyId: string): TripHistoryCacheEntry {
  ensureTeardownListener();
  const existingEntry = tripHistoryCache.get(companyId);
  if (existingEntry) {
    if (existingEntry.releaseTimer) {
      clearTimeout(existingEntry.releaseTimer);
      existingEntry.releaseTimer = null;
    }
    if (!existingEntry.unsubscribe && !existingEntry.retryTimer) {
      subscribeEntry(companyId, existingEntry);
    }
    return existingEntry;
  }

  const entry: TripHistoryCacheEntry = {
    trips: readPersistedTrips(companyId),
    loading: true,
    error: null,
    listeners: new Set(),
    unsubscribe: null,
    releaseTimer: null,
    retryTimer: null,
    retryAttempt: 0,
  };
  tripHistoryCache.set(companyId, entry);
  subscribeEntry(companyId, entry);

  return entry;
}

function getState(entry: TripHistoryCacheEntry): TripHistoryState {
  return {
    trips: entry.trips,
    loading: entry.loading,
    error: entry.error,
  };
}

function getInitialState(companyId: string | null | undefined): TripHistoryState {
  if (!companyId) return EMPTY_STATE;

  const cachedEntry = tripHistoryCache.get(companyId);
  if (cachedEntry) return getState(cachedEntry);
  const persistedTrips = readPersistedTrips(companyId);
  return { trips: persistedTrips, loading: true, error: null };
}

type HookState = {
  companyId: string | null;
  value: TripHistoryState;
};

export function useTripHistory(
  companyId: string | null | undefined,
  options: { enabled?: boolean } = {},
) {
  ensureTeardownListener();
  const enabled = options.enabled !== false;
  const normalizedCompanyId = enabled ? companyId ?? null : null;
  const [state, setState] = useState<HookState>(() => ({
    companyId: normalizedCompanyId,
    value: getInitialState(normalizedCompanyId),
  }));

  useLayoutEffect(() => {
    if (!enabled || !companyId || isAuthTeardownActive()) {
      setState({ companyId: null, value: EMPTY_STATE });
      return;
    }

    const entry = ensureEntry(companyId);
    const updateState = () =>
      setState({ companyId, value: getState(entry) });

    entry.listeners.add(updateState);
    if (!entry.unsubscribe && !entry.retryTimer) {
      subscribeEntry(companyId, entry);
    }
    updateState();

    return () => {
      entry.listeners.delete(updateState);
      if (entry.listeners.size === 0) {
        scheduleEntryRelease(companyId, entry);
      }
    };
  }, [companyId, enabled]);

  const visibleState = state.companyId === normalizedCompanyId
    ? state.value
    : getInitialState(companyId);

  return {
    historicoTrips: visibleState.trips,
    loading: visibleState.loading,
    error: visibleState.error,
  };
}
