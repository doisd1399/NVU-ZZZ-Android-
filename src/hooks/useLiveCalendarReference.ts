import { useEffect, useState } from "react";

export type LiveCalendarMode = "local" | "utc";

/**
 * Keeps date-based periods aligned with their canonical calendar boundary.
 * Ranking consumers use UTC so every device advances to the next day, week
 * and month at the same instant. Other screens may keep the local default.
 */
export function useLiveCalendarReference(mode: LiveCalendarMode = "local") {
  const [referenceDate, setReferenceDate] = useState(() => new Date());

  useEffect(() => {
    let midnightTimer: ReturnType<typeof setTimeout> | null = null;

    const refresh = () => setReferenceDate(new Date());
    const scheduleMidnightRefresh = () => {
      if (midnightTimer) clearTimeout(midnightTimer);
      const now = new Date();
      const nextMidnight =
        mode === "utc"
          ? new Date(
              Date.UTC(
                now.getUTCFullYear(),
                now.getUTCMonth(),
                now.getUTCDate() + 1,
                0,
                0,
                1,
              ),
            )
          : new Date(now);
      if (mode === "local") {
        nextMidnight.setDate(nextMidnight.getDate() + 1);
        nextMidnight.setHours(0, 0, 1, 0);
      }
      midnightTimer = setTimeout(() => {
        refresh();
        scheduleMidnightRefresh();
      }, Math.max(1_000, nextMidnight.getTime() - now.getTime()));
    };

    const handleVisibility = () => {
      if (!document.hidden) refresh();
    };

    scheduleMidnightRefresh();
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", handleVisibility);

    return () => {
      if (midnightTimer) clearTimeout(midnightTimer);
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", handleVisibility);
    };
  }, [mode]);

  return referenceDate;
}
