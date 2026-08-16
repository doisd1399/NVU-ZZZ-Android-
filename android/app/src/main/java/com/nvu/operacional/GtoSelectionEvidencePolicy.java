package com.nvu.operacional;

/**
 * HF26 safety gate: visual change may corroborate a selection, but it can never create
 * one without a real driver action. Every confirmed freight identity must be traceable
 * to a touch/outside-touch/replacement-touch source.
 */
final class GtoSelectionEvidencePolicy {
    private GtoSelectionEvidencePolicy() {}

    static boolean isHumanBackedSource(String source) {
        if (source == null) return false;
        String s = source.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty()) return false;
        return s.contains("precise-touch")
            || s.contains("outside-touch")
            || s.contains("touch-marker")
            || s.contains("touch-probe")
            || s.contains("touch-pulse")
            || s.contains("replacement-touch")
            || s.contains("bootstrap-touch");
    }

    static boolean mayConfirmSelection(boolean humanActionObserved, int row, int rowCount) {
        return humanActionObserved && row >= 0 && row < rowCount;
    }

    static boolean mayRestoreConfirmedSelection(String status, String source) {
        return "CONFIRMED".equals(status) && isHumanBackedSource(source);
    }
}
