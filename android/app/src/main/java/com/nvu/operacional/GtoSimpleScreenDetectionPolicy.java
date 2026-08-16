package com.nvu.operacional;

/**
 * HF16: small, state-oriented rules for screen transitions.
 *
 * The detector deliberately prefers a few strong semantic/interaction signals over
 * broad color/layout signatures.  It has no Android dependencies so the invariants
 * can be regression-tested without an emulator.
 */
final class GtoSimpleScreenDetectionPolicy {
    private GtoSimpleScreenDetectionPolicy() {}

    static boolean isCompletedResult(boolean completionWord, boolean monetaryValuePresent) {
        return completionWord && monetaryValuePresent;
    }

    static boolean isStableFreightListReturn(
        String state,
        boolean listPresent,
        int observedFrames,
        long visibleForMs
    ) {
        return "TRIP_IN_PROGRESS".equals(state)
            && listPresent
            && observedFrames >= 2
            && visibleForMs >= 180L;
    }

    static boolean mayReplaceCancelledTripOnNewAccept(
        String state,
        boolean listCandidateArmed,
        boolean stableReturnedList,
        boolean touchOrPressedRowEvidence,
        boolean exactAcceptRowEvidence
    ) {
        return "TRIP_IN_PROGRESS".equals(state)
            && listCandidateArmed
            && touchOrPressedRowEvidence
            && (stableReturnedList || exactAcceptRowEvidence);
    }
}
