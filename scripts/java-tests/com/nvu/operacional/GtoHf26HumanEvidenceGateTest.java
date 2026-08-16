package com.nvu.operacional;

/** HF26 production invariant: no human action, no selected freight. */
public final class GtoHf26HumanEvidenceGateTest {
    private static int checks = 0;
    private static void req(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        // Hundreds of visual candidates may exist while the truck/camera moves. They can
        // never create a freight identity without a real driver action.
        for (int frame = 0; frame < 600; frame++) {
            for (int row = 0; row < 5; row++) {
                req(!GtoSelectionEvidencePolicy.mayConfirmSelection(false, row, 5),
                    "visual-only frame must not confirm row " + row);
            }
        }

        // Real driver action may authorize only an in-range row.
        for (int row = 0; row < 5; row++) {
            req(GtoSelectionEvidencePolicy.mayConfirmSelection(true, row, 5),
                "human-backed row must be eligible: " + row);
        }
        req(!GtoSelectionEvidencePolicy.mayConfirmSelection(true, -1, 5), "NONE stays NONE");
        req(!GtoSelectionEvidencePolicy.mayConfirmSelection(true, 5, 5), "out-of-range blocked");

        // Source provenance is part of durability. Legacy visual-only identities may not
        // be restored as confirmed freight after an upgrade/process restart.
        req(GtoSelectionEvidencePolicy.isHumanBackedSource("precise-touch"), "precise touch trusted");
        req(GtoSelectionEvidencePolicy.isHumanBackedSource("outside-touch+visual-buffer"), "outside touch trusted");
        req(GtoSelectionEvidencePolicy.isHumanBackedSource("replacement-touch+evidence-retry"), "replacement touch trusted");
        req(!GtoSelectionEvidencePolicy.isHumanBackedSource("frame-lock"), "frame lock alone untrusted");
        req(!GtoSelectionEvidencePolicy.isHumanBackedSource("visual-buffer"), "visual buffer alone untrusted");
        req(!GtoSelectionEvidencePolicy.isHumanBackedSource("visual-press-row-3"), "visual press alone untrusted");
        req(!GtoSelectionEvidencePolicy.isHumanBackedSource(""), "empty source untrusted");

        req("WAITING_FREIGHT".equals(GtoSessionRecoveryPolicy.restoredState(
            "CONFIRMING_FREIGHT", "CONFIRMED", "REVIEW_REQUIRED", "ORIGIN_COMPANY", "frame-lock"
        )), "legacy visual review must not restore");
        req("CONFIRMING_FREIGHT".equals(GtoSessionRecoveryPolicy.restoredState(
            "CONFIRMING_FREIGHT", "CONFIRMED", "REVIEW_REQUIRED", "ORIGIN_COMPANY", "precise-touch"
        )), "human-backed review restores");

        // N or NONE, never M != N.
        for (int touched = 0; touched < 5; touched++) {
            for (int visual = -1; visual < 5; visual++) {
                int resolved = GtoSelectionIdentityPolicy.resolveExactTouchAfterTransition(
                    touched, 5, true, visual
                );
                req(resolved == touched || resolved == -1,
                    "touch row may resolve only to itself or NONE");
                if (visual >= 0 && visual != touched) {
                    req(resolved == -1, "different visual row must reject selection");
                }
            }
        }

        // A freight list requires semantic evidence, not orange geometry alone.
        req(!GtoFreightSemanticCertificationPolicy.isCertifiedPage(1, 1, 0),
            "orange candidate without value is not a list");
        req(GtoFreightSemanticCertificationPolicy.isCertifiedPage(1, 1, 1),
            "single row with monetary anchor can certify");
        req(GtoFreightSemanticCertificationPolicy.isCertifiedPage(5, 5, 5),
            "five real freight rows certify");

        // Selected-row fallback requires the simple semantic pair requested for a real list:
        // visual Accept geometry + OCR evidence of the word Aceitar + same-row money/context.
        req(!GtoFreightSemanticCertificationPolicy.selectedRowCanCertify(
            true, true, "Carga Tijolo", "Fábrica", "Área Rural", "600Km", ""
        ), "row without money cannot certify");
        req(!GtoFreightSemanticCertificationPolicy.selectedRowCanCertify(
            true, false, "Carga Tijolo", "Fábrica", "Área Rural", "600Km", "R$ 11.600"
        ), "orange geometry without Aceitar text cannot certify");
        req(GtoFreightSemanticCertificationPolicy.selectedRowCanCertify(
            true, true, "Carga Tijolo", "Fábrica", "Área Rural", "600Km", "R$ 11.600"
        ), "Aceitar + money + context certifies selected row");

        // REVIEW_REQUIRED is last-mile only. It cannot build an almost empty freight.
        req(!GtoFreightReviewEligibilityPolicy.mayAskDriver("Soja", "", "", "", ""),
            "one automatic field cannot enter manual review");
        req(!GtoFreightReviewEligibilityPolicy.mayAskDriver("Soja", "Metalurgica", "", "", ""),
            "two automatic fields cannot enter manual review");
        req(GtoFreightReviewEligibilityPolicy.mayAskDriver("Soja", "Metalurgica", "Nova Macae", "", ""),
            "three strong fields may review the remaining two");
        req(GtoFreightReviewEligibilityPolicy.mayAskDriver("Soja", "Metalurgica", "Nova Macae", "600Km", ""),
            "four strong fields may review one");

        // Exact garbage captured in the rejected physical run must never count as a
        // meaningful automatic/manual freight value.
        req(!GtoFreightReviewPolicy.isAutomaticTextUsable("Oi"), "Oi is OCR noise");
        req(!GtoFreightReviewPolicy.isAutomaticTextUsable("9i"), "9i is OCR noise");
        req(!GtoFreightReviewPolicy.isManualValueValid(GtoFreightReviewPolicy.ORIGIN_COMPANY, "Oi"),
            "Oi cannot be saved manually as origin");
        req(!GtoFreightReviewPolicy.isManualValueValid(GtoFreightReviewPolicy.CARGO, "9i"),
            "9i cannot be saved manually as cargo");

        System.out.println("GtoHf26HumanEvidenceGateTest: PASS (" + checks + " assertions)");
    }
}
