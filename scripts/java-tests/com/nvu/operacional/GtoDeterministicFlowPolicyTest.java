package com.nvu.operacional;

public final class GtoDeterministicFlowPolicyTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        require(GtoDeterministicFlowPolicy.shouldPrepareWaitingBeforeGtoOpen("IDLE"), "IDLE must be prepared before GTO opens");
        require(GtoDeterministicFlowPolicy.shouldPrepareWaitingBeforeGtoOpen("CANCELLED"), "CANCELLED must be prepared before GTO opens");
        require(!GtoDeterministicFlowPolicy.shouldPrepareWaitingBeforeGtoOpen("TRIP_IN_PROGRESS"), "active trip must never be reset when GTO reopens");
        require(GtoDeterministicFlowPolicy.isPreparedForGtoOpen("WAITING_FREIGHT"), "WAITING_FREIGHT is launch-ready");
        require(GtoDeterministicFlowPolicy.isPreparedForGtoOpen("TRIP_IN_PROGRESS"), "active trip is launch-ready without reset");
        require(!GtoDeterministicFlowPolicy.isPreparedForGtoOpen("IDLE"), "raw IDLE is not launch-ready");
        require(GtoDeterministicFlowPolicy.useOrderedFreightFrames("WAITING_FREIGHT"), "freight-selection frames must be consumed in order");
        require(!GtoDeterministicFlowPolicy.useOrderedFreightFrames("TRIP_IN_PROGRESS"), "route frames may use latest-frame sampling");
        require(GtoDeterministicFlowPolicy.mayAutoBootstrapFreightList("IDLE"), "IDLE may bootstrap a real freight list");
        require(GtoDeterministicFlowPolicy.mayAutoBootstrapFreightList("CANCELLED"), "CANCELLED may bootstrap a real freight list");
        require(!GtoDeterministicFlowPolicy.mayAutoBootstrapFreightList("CONFIRMING_FREIGHT"), "confirmation must never bootstrap/restart from live list frames");
        require(!GtoDeterministicFlowPolicy.mayObserveFreightListOutsideWaiting("CONFIRMING_FREIGHT"), "confirmation owns its frozen transaction");
        require(!GtoDeterministicFlowPolicy.freightListIsInformationalOnly("TRIP_IN_PROGRESS", false), "reopened list during trip is a lifecycle boundary");
        require(!GtoDeterministicFlowPolicy.mayReplaceActiveTrip("TRIP_IN_PROGRESS", false), "one unconfirmed list candidate cannot replace the trip");
        require(GtoDeterministicFlowPolicy.mayReplaceActiveTrip("TRIP_IN_PROGRESS", true), "stable returned jobs list may close the stale trip");
        require(!GtoDeterministicFlowPolicy.mayReplaceActiveTrip("CONFIRMING_FREIGHT", true), "confirmation owns its frozen selected-row transaction");
        require(GtoDeterministicFlowPolicy.mayInterpretResultScreen("TRIP_IN_PROGRESS"), "result is meaningful during a trip");
        require(!GtoDeterministicFlowPolicy.mayInterpretResultScreen("WAITING_FREIGHT"), "result-like pixels cannot alter freight-selection state");
        require(GtoDeterministicFlowPolicy.mayInterpretBonusOrAds("RESULT_DETECTED"), "bonus/ad is meaningful only after result");
        require(!GtoDeterministicFlowPolicy.mayInterpretBonusOrAds("TRIP_IN_PROGRESS"), "ad-like pixels during trip are neutral");
        require(GtoDeterministicFlowPolicy.unknownScreenMustBeNeutral("TRIP_IN_PROGRESS"), "unknown trip screen must be neutral");
        require(GtoDeterministicFlowPolicy.unknownScreenMustBeNeutral("CONFIRMING_FREIGHT"), "unknown confirmation screen must be neutral");

        // Canonical state graph: the normal journey may only advance one valid edge at a time.
        require(GtoDeterministicFlowPolicy.isAllowedTripTransition("IDLE", "WAITING_FREIGHT"), "IDLE -> WAITING_FREIGHT is valid");
        require(GtoDeterministicFlowPolicy.isAllowedTripTransition("WAITING_FREIGHT", "CONFIRMING_FREIGHT"), "WAITING_FREIGHT -> CONFIRMING_FREIGHT is valid");
        require(GtoDeterministicFlowPolicy.isAllowedTripTransition("CONFIRMING_FREIGHT", "TRIP_IN_PROGRESS"), "CONFIRMING_FREIGHT -> TRIP_IN_PROGRESS is valid");
        require(GtoDeterministicFlowPolicy.isAllowedTripTransition("TRIP_IN_PROGRESS", "RESULT_DETECTED"), "TRIP_IN_PROGRESS -> RESULT_DETECTED is valid");
        require(GtoDeterministicFlowPolicy.isAllowedTripTransition("RESULT_DETECTED", "RESULT_CONFIRMED"), "RESULT_DETECTED -> RESULT_CONFIRMED is valid");
        require(!GtoDeterministicFlowPolicy.isAllowedTripTransition("WAITING_FREIGHT", "RESULT_DETECTED"), "WAITING_FREIGHT cannot jump to result");
        require(!GtoDeterministicFlowPolicy.isAllowedTripTransition("CONFIRMING_FREIGHT", "RESULT_DETECTED"), "CONFIRMING_FREIGHT cannot jump to result");
        require(!GtoDeterministicFlowPolicy.isAllowedTripTransition("IDLE", "TRIP_IN_PROGRESS"), "IDLE cannot jump directly to trip");
        require(GtoDeterministicFlowPolicy.mayUseVisualFreightProof(true, true, true, false, false, 6), "current GTO layout may expose six visible freight rows");
        require(!GtoDeterministicFlowPolicy.mayUseVisualFreightProof(true, true, true, false, false, 7), "unsupported row cardinality must fail closed instead of guessing");
        require(GtoDeterministicFlowPolicy.stabilizeVisibleFreightCount(2, 1, true) == 2, "pressed 2-to-1 animation must keep the real two-row count");
        require(GtoDeterministicFlowPolicy.stabilizeVisibleFreightCount(5, 4, true) == 5, "pressed N-to-N-1 animation must keep the baseline count");
        require(GtoDeterministicFlowPolicy.stabilizeVisibleFreightCount(5, 4, false) == 4, "a real cardinality change must not be invented back to five");

        System.out.println("GtoDeterministicFlowPolicyTest: PASS");
    }
}
