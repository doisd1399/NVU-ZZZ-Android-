package com.nvu.operacional;

/** Prevents queued old frames from replacing newer journey context on slow devices. */
final class GtoFrameFreshnessPolicy {
    static final long NORMAL_MAX_AGE_MS = 520L;
    static final long CRITICAL_TOUCH_MAX_AGE_MS = 1350L;

    private GtoFrameFreshnessPolicy() {}

    static boolean shouldConsume(long nowNs, long imageTimestampNs, boolean criticalTouchWindow) {
        if (imageTimestampNs <= 0L || nowNs <= 0L || imageTimestampNs > nowNs) return true;
        long ageMs = (nowNs - imageTimestampNs) / 1_000_000L;
        long maxAge = criticalTouchWindow ? CRITICAL_TOUCH_MAX_AGE_MS : NORMAL_MAX_AGE_MS;
        return ageMs <= maxAge;
    }
}
