package com.nvu.operacional;

/** HF36 regression: a field missed by both initial reads may be recovered by a focused reread. */
public final class GtoHf36CargoRecoveryPolicyTest {
    private static void req(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        GtoFreightFieldConflictPolicy.Resolution cargo = GtoFreightFieldConflictPolicy.resolve(
            GtoFreightReviewPolicy.CARGO, "", "", "Tijolos Maciços"
        );
        req(cargo.resolved, "missing cargo must be recoverable from focused selected-row reread");
        req("Tijolos Maciços".equals(cargo.value), "focused cargo must remain literal");
        req("FOCUSED_RETRY".equals(cargo.source), "cargo recovery must record focused retry source");

        GtoFreightFieldConflictPolicy.Resolution destination = GtoFreightFieldConflictPolicy.resolve(
            GtoFreightReviewPolicy.DESTINATION, "", "", "Cruz do Oeste"
        );
        req(destination.resolved && "Cruz do Oeste".equals(destination.value),
            "same recovery rule must remain generic for operational text fields");

        GtoFreightFieldConflictPolicy.Resolution invalid = GtoFreightFieldConflictPolicy.resolve(
            GtoFreightReviewPolicy.CARGO, "", "", "OI"
        );
        req(!invalid.resolved, "obvious OCR noise must not be promoted as cargo");

        GtoFreightFieldConflictPolicy.Resolution agreed = GtoFreightFieldConflictPolicy.resolve(
            GtoFreightReviewPolicy.CARGO, "CARGA TIJOLO", "CARGA TIJOLO", "outra leitura"
        );
        req(agreed.resolved && "CARGA TIJOLO".equals(agreed.value),
            "focused retry must not overwrite agreeing initial evidence");

        System.out.println("GtoHf36CargoRecoveryPolicyTest: PASS");
    }
}
