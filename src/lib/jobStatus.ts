export const OPEN_JOB_STATUSES = [
  "pending",
  "active",
  "awaiting_completion",
  "delayed",
] as const;

export const RUNNING_JOB_STATUSES = [
  "active",
  "awaiting_completion",
  "delayed",
] as const;

export const TRIP_RECORDABLE_JOB_STATUSES = ["active", "delayed"] as const;

export const TERMINAL_JOB_STATUSES = ["completed", "cancelled"] as const;

export const normalizeJobStatus = (status: unknown): string =>
  String(status || "").trim().toLowerCase();

export const isOpenJobStatus = (status: unknown): boolean =>
  (OPEN_JOB_STATUSES as readonly string[]).includes(normalizeJobStatus(status));

export const isRunningJobStatus = (status: unknown): boolean =>
  (RUNNING_JOB_STATUSES as readonly string[]).includes(normalizeJobStatus(status));

export const isTripRecordableJobStatus = (status: unknown): boolean =>
  (TRIP_RECORDABLE_JOB_STATUSES as readonly string[]).includes(
    normalizeJobStatus(status),
  );

export const isTerminalJobStatus = (status: unknown): boolean =>
  (TERMINAL_JOB_STATUSES as readonly string[]).includes(normalizeJobStatus(status));
