package com.nvu.operacional;

/**
 * HF25: resolves field conflicts only with literal evidence from the already-selected row.
 * No fuzzy correction is allowed. When two independent reads disagree, a focused retry
 * must agree with one of them before the field can be auto-confirmed.
 */
final class GtoFreightFieldConflictPolicy {
    static final class Resolution {
        final boolean resolved;
        final String value;
        final String source;

        Resolution(boolean resolved, String value, String source) {
            this.resolved = resolved;
            this.value = value == null ? "" : value.trim();
            this.source = source == null ? "" : source;
        }

        static Resolution unresolved() {
            return new Resolution(false, "", "UNRESOLVED");
        }
    }

    private GtoFreightFieldConflictPolicy() {}

    static boolean needsRetry(String field, String exact, String frozen) {
        if (!valid(field, exact) || !valid(field, frozen)) return false;
        return !same(field, exact, frozen);
    }

    static Resolution resolve(String field, String exact, String frozen, String retry) {
        boolean exactValid = valid(field, exact);
        boolean frozenValid = valid(field, frozen);
        boolean retryValid = valid(field, retry);

        if (exactValid && !frozenValid) return new Resolution(true, exact, "SELECTED_ROW_OCR");
        if (!exactValid && frozenValid) return new Resolution(true, frozen, "FROZEN_TOUCH_BASELINE");
        if (!exactValid && !frozenValid) {
            return retryValid ? new Resolution(true, retry, "FOCUSED_RETRY") : Resolution.unresolved();
        }
        if (same(field, exact, frozen)) return new Resolution(true, exact, "AGREED_INITIAL_READS");

        // Initial reads conflict. Never choose either side without a focused retry.
        if (!retryValid) return Resolution.unresolved();
        if (same(field, retry, exact)) return new Resolution(true, exact, "FOCUSED_RETRY_CONFIRMED_SELECTED");
        if (same(field, retry, frozen)) return new Resolution(true, frozen, "FOCUSED_RETRY_CONFIRMED_FROZEN");
        return Resolution.unresolved();
    }

    private static boolean same(String field, String first, String second) {
        if (GtoFreightReviewPolicy.DISTANCE.equals(field)) {
            return GtoFreightTextGuard.sameNumericValue(first, second);
        }
        if (GtoFreightReviewPolicy.VALUE.equals(field)) {
            Long a = GtoMoneyValue.parseCents(first);
            Long b = GtoMoneyValue.parseCents(second);
            return a != null && b != null && a.longValue() == b.longValue();
        }
        return GtoFreightTextGuard.sameVisibleText(first, second);
    }

    private static boolean valid(String field, String value) {
        return GtoFreightReviewPolicy.isManualValueValid(field, value);
    }
}
