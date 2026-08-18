package com.nvu.operacional;

/**
 * Pure policy helpers for the deterministic GTO state machine.
 *
 * This class deliberately has no Android dependencies so the critical transition rules
 * can be regression-tested on Windows/Linux without an emulator. The observer owns the
 * actual state; this policy only answers whether a visual event is allowed to influence it.
 */
final class GtoDeterministicFlowPolicy {
    private GtoDeterministicFlowPolicy() {}

    static boolean isAllowedTripTransition(String from, String to) {
        if (to == null || to.isEmpty()) return false;
        String safeFrom = from == null || from.isEmpty() ? "IDLE" : from;
        if (safeFrom.equals(to)) return true;
        if ("IDLE".equals(safeFrom)) {
            return "WAITING_FREIGHT".equals(to) || "CANCELLED".equals(to);
        }
        if ("WAITING_FREIGHT".equals(safeFrom)) {
            return "CONFIRMING_FREIGHT".equals(to) || "CANCELLED".equals(to) || "IDLE".equals(to);
        }
        if ("CONFIRMING_FREIGHT".equals(safeFrom)) {
            return "TRIP_IN_PROGRESS".equals(to) || "WAITING_FREIGHT".equals(to)
                || "CANCELLED".equals(to) || "IDLE".equals(to);
        }
        if ("TRIP_IN_PROGRESS".equals(safeFrom)) {
            return "RESULT_DETECTED".equals(to) || "REJECTED_BONUS".equals(to)
                || "WAITING_FREIGHT".equals(to) || "CANCELLED".equals(to);
        }
        if ("RESULT_DETECTED".equals(safeFrom)) {
            return "AWAITING_BONUS_VALIDATION".equals(to) || "RESULT_CONFIRMED".equals(to)
                || "REJECTED_BONUS".equals(to) || "WAITING_FREIGHT".equals(to)
                || "CANCELLED".equals(to);
        }
        if ("AWAITING_BONUS_VALIDATION".equals(safeFrom)) {
            return "RESULT_CONFIRMED".equals(to) || "REJECTED_BONUS".equals(to)
                || "RESULT_DETECTED".equals(to) || "WAITING_FREIGHT".equals(to)
                || "CANCELLED".equals(to);
        }
        if ("RESULT_CONFIRMED".equals(safeFrom) || "REJECTED_BONUS".equals(safeFrom)) {
            return "IDLE".equals(to) || "WAITING_FREIGHT".equals(to) || "CANCELLED".equals(to);
        }
        if ("CANCELLED".equals(safeFrom)) {
            return "IDLE".equals(to) || "WAITING_FREIGHT".equals(to);
        }
        return false;
    }

    static boolean mayRepairWaitingToTrip(
        String state,
        boolean durableFreightLocked,
        boolean durableFreightRestored
    ) {
        return "WAITING_FREIGHT".equals(state)
            && durableFreightLocked
            && durableFreightRestored;
    }

    static boolean shouldPrepareWaitingBeforeGtoOpen(String state) {
        return "IDLE".equals(state) || "CANCELLED".equals(state);
    }

    static boolean isPreparedForGtoOpen(String state) {
        return "WAITING_FREIGHT".equals(state)
            || "CONFIRMING_FREIGHT".equals(state)
            || "TRIP_IN_PROGRESS".equals(state)
            || "RESULT_DETECTED".equals(state)
            || "AWAITING_BONUS_VALIDATION".equals(state)
            || "RESULT_CONFIRMED".equals(state);
    }

    static boolean useOrderedFreightFrames(String state) {
        return "WAITING_FREIGHT".equals(state);
    }

    static boolean mayAutoBootstrapFreightList(String state) {
        return shouldPrepareWaitingBeforeGtoOpen(state);
    }

    static boolean mayObserveFreightListOutsideWaiting(String state) {
        return mayAutoBootstrapFreightList(state)
            || "TRIP_IN_PROGRESS".equals(state)
            || "RESULT_DETECTED".equals(state)
            || "AWAITING_BONUS_VALIDATION".equals(state);
    }


    static boolean mayProbeFreightListForCurrentState(String state, boolean ignoredExplicitReplacementFlag) {
        // HF34: recognition of the GTO jobs list is never conditional on a menu/button.
        // The list is a first-class lifecycle state and must always be observable.
        return mayObserveFreightListOutsideWaiting(state);
    }

    static boolean shouldAutoPrepareNextFreightAfterSync(
        String state,
        boolean syncAcknowledged,
        boolean operationClosed
    ) {
        return "RESULT_CONFIRMED".equals(state) && syncAcknowledged && !operationClosed;
    }

    static boolean mayPrepareNextFreightAfterSealedQueue(
        String state,
        boolean payloadSealed,
        boolean operationClosed,
        int completedBeforeThisTrip,
        int totalDeliveries
    ) {
        if (!"RESULT_CONFIRMED".equals(state) || !payloadSealed || operationClosed) return false;
        if (totalDeliveries <= 0) return false;
        int safeCompleted = Math.max(0, completedBeforeThisTrip);
        return safeCompleted + 1 < totalDeliveries;
    }

    static boolean shouldRefreshTransientVisualContextAfterReturn(String state) {
        return "WAITING_FREIGHT".equals(state)
            || "CONFIRMING_FREIGHT".equals(state)
            || "TRIP_IN_PROGRESS".equals(state)
            || "RESULT_DETECTED".equals(state)
            || "AWAITING_BONUS_VALIDATION".equals(state);
    }

    static boolean mayReplaceActiveTrip(String state, boolean stableFreightListReturned) {
        return stableFreightListReturned && "TRIP_IN_PROGRESS".equals(state);
    }

    static boolean freightListIsInformationalOnly(String state, boolean ignored) {
        return false;
    }

    static boolean mayInterpretResultScreen(String state) {
        return "TRIP_IN_PROGRESS".equals(state)
            || "RESULT_DETECTED".equals(state)
            || "AWAITING_BONUS_VALIDATION".equals(state);
    }

    static boolean mayInterpretBonusOrAds(String state) {
        return "RESULT_DETECTED".equals(state)
            || "AWAITING_BONUS_VALIDATION".equals(state);
    }

    static boolean unknownScreenMustBeNeutral(String state) {
        return state != null && !state.isEmpty();
    }

    static int stabilizeVisibleFreightCount(int baselineCount, int currentCount, boolean isolatedMissingRow) {
        int safeCurrent = Math.max(0, currentCount);
        int safeBaseline = Math.max(0, baselineCount);
        if (isolatedMissingRow && safeBaseline == safeCurrent + 1) return safeBaseline;
        return safeCurrent;
    }

    static boolean mayUseVisualFreightProof(
        boolean waitingForFreight,
        boolean projectionActive,
        boolean packageMatchesGto,
        boolean packageUnknown,
        boolean permissionReturnFromNvu,
        int freightCount
    ) {
        if (!waitingForFreight || !projectionActive) return false;
        if (freightCount < 1 || freightCount > 6) return false;
        // Pixels may bridge a missing/stale UsageStats event only when Android has not
        // positively identified another foreground app, or during the bounded return
        // from the MediaProjection permission flow. A known non-GTO foreground app is
        // authoritative and pauses analysis.
        return packageMatchesGto || packageUnknown || permissionReturnFromNvu;
    }
}
