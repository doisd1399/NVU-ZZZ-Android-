export type RankingCalendarPeriod = "semana" | "mes";

const DAY_MS = 24 * 60 * 60 * 1000;

function pad(value: number): string {
  return String(value).padStart(2, "0");
}

function parseCalendarDateUtc(value: Date | string | number): Date {
  if (typeof value === "string") {
    const trimmed = value.trim();
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(trimmed);
    if (match) {
      return new Date(
        Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])),
      );
    }
  }

  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? new Date(0) : parsed;
}

export function getRankingUtcStartOfDay(
  value: Date | string | number,
): Date {
  const date = parseCalendarDateUtc(value);
  return new Date(
    Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()),
  );
}

export function getRankingUtcEndOfDay(
  value: Date | string | number,
): Date {
  return new Date(getRankingUtcStartOfDay(value).getTime() + DAY_MS - 1);
}

/**
 * Canonical weekly ranking window shared by every device and Cloud Function.
 * The existing NVU season convention is preserved: Sunday 00:00:00.000 UTC
 * through Saturday 23:59:59.999 UTC.
 */
export function getRankingUtcWeeklyRange(referenceDate = new Date()) {
  const start = getRankingUtcStartOfDay(referenceDate);
  start.setUTCDate(start.getUTCDate() - start.getUTCDay());
  return {
    start,
    end: new Date(start.getTime() + 7 * DAY_MS - 1),
  };
}

export function getRankingUtcMonthlyRange(referenceDate = new Date()) {
  const start = new Date(
    Date.UTC(referenceDate.getUTCFullYear(), referenceDate.getUTCMonth(), 1),
  );
  const nextMonth = new Date(
    Date.UTC(referenceDate.getUTCFullYear(), referenceDate.getUTCMonth() + 1, 1),
  );
  return {
    start,
    end: new Date(nextMonth.getTime() - 1),
  };
}

export function getRankingUtcCustomRange(
  startValue: Date | string,
  endValue: Date | string,
) {
  const start = getRankingUtcStartOfDay(startValue);
  const end = getRankingUtcEndOfDay(endValue);
  return start <= end ? { start, end } : { start: getRankingUtcStartOfDay(endValue), end: getRankingUtcEndOfDay(startValue) };
}

export function addRankingUtcDays(value: Date, amount: number): Date {
  const date = new Date(value);
  date.setUTCDate(date.getUTCDate() + amount);
  return date;
}

export function addRankingUtcMonths(value: Date, amount: number): Date {
  return new Date(
    Date.UTC(value.getUTCFullYear(), value.getUTCMonth() + amount, 1),
  );
}

export function buildRankingUtcPeriodKey(
  periodType: RankingCalendarPeriod,
  referenceDate = new Date(),
): string {
  if (periodType === "mes") {
    return `mes_${referenceDate.getUTCFullYear()}-${pad(referenceDate.getUTCMonth() + 1)}`;
  }

  const start = getRankingUtcWeeklyRange(referenceDate).start;
  return `semana_${start.getUTCFullYear()}-${pad(start.getUTCMonth() + 1)}-${pad(start.getUTCDate())}`;
}
