package com.nvu.operacional;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class GtoHf33RealtimeFlowPolicyTest {
    private static void req(boolean v, String m) { if (!v) throw new AssertionError(m); }

    public static void main(String[] args) {
        req("Metalúrgica".equals(GtoFreightContextPolicy.unanimousOrigin(
            Arrays.asList("Metalúrgica", "METALURGICA", "Metalurgica"), 5)),
            "three agreeing peer rows must recover the literal origin");
        req(GtoFreightContextPolicy.unanimousOrigin(
            Arrays.asList("Metalúrgica", "Fábrica de Tijolo", "Metalúrgica"), 5).isEmpty(),
            "any readable origin conflict must fail closed");
        req(GtoFreightContextPolicy.unanimousOrigin(
            Collections.singletonList("Metalúrgica"), 5).isEmpty(),
            "one peer row is not enough context");

        List<GtoOriginGeometryPolicy.RowLine> splitRoute = Arrays.asList(
            new GtoOriginGeometryPolicy.RowLine("Barras de Ferro", 10, 25, 0, 100),
            new GtoOriginGeometryPolicy.RowLine("Metalúrgica", 38, 51, 0, 95),
            new GtoOriginGeometryPolicy.RowLine("Dalavan", 38, 51, 125, 195),
            new GtoOriginGeometryPolicy.RowLine("Águas Velhas", 70, 84, 0, 105)
        );
        GtoOriginGeometryPolicy.Result split = GtoOriginGeometryPolicy.inferFromRowLines(
            splitRoute, "", 0, 100
        );
        req(split.strong && "Metalúrgica".equals(split.value),
            "horizontal route fragments must recover exact source company");
        req("ROW_ROI_HORIZONTAL_ROUTE_SPLIT".equals(split.source),
            "horizontal origin must be explicit geometry evidence");

        List<GtoOriginGeometryPolicy.RowLine> ambiguous = Arrays.asList(
            new GtoOriginGeometryPolicy.RowLine("Barras de Ferro", 10, 25, 0, 100),
            new GtoOriginGeometryPolicy.RowLine("Metalúrgica Dalavan", 40, 52, 0, 190),
            new GtoOriginGeometryPolicy.RowLine("Águas Velhas", 70, 84, 0, 105)
        );
        GtoOriginGeometryPolicy.Result ambiguousResult = GtoOriginGeometryPolicy.inferFromRowLines(
            ambiguous, "", 0, 100
        );
        req(!ambiguousResult.strong && ambiguousResult.value.isEmpty(),
            "a merged source+destination route phrase must stay unresolved until separator, geometry or page context recovers the source");

        req(GtoDeterministicFlowPolicy.mayAutoBootstrapFreightList("IDLE"),
            "IDLE must allow automatic freight-list bootstrap");
        req(GtoDeterministicFlowPolicy.mayAutoBootstrapFreightList("CANCELLED"),
            "CANCELLED must allow automatic freight-list bootstrap");
        req(!GtoDeterministicFlowPolicy.freightListIsInformationalOnly("TRIP_IN_PROGRESS", false),
            "freight list during active trip is a canonical lifecycle boundary");
        req(!GtoDeterministicFlowPolicy.mayReplaceActiveTrip("TRIP_IN_PROGRESS", false),
            "one unconfirmed frame cannot replace the active trip");
        req(GtoDeterministicFlowPolicy.mayReplaceActiveTrip("TRIP_IN_PROGRESS", true),
            "stable returned list closes the active trip without a separate manual arm");

        System.out.println("GtoHf33RealtimeFlowPolicyTest: PASS");
    }
}
