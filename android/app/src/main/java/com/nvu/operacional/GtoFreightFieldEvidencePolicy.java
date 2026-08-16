package com.nvu.operacional;

/**
 * Field-level evidence policy for an already-selected freight row.
 * A field is accepted independently. Global frame consensus is never used to
 * manufacture confidence for another field, and destinationCompany is optional.
 */
final class GtoFreightFieldEvidencePolicy {
    private GtoFreightFieldEvidencePolicy() {}

    static boolean text(String value, int votes, boolean selectedRowEvidence) {
        return validText(value) && (votes >= 2 || selectedRowEvidence);
    }

    static boolean distance(String value, int votes, boolean selectedRowEvidence) {
        return GtoFreightReviewPolicy.isManualValueValid(GtoFreightReviewPolicy.DISTANCE, value)
            && (votes >= 2 || selectedRowEvidence);
    }

    static boolean money(String value, int votes, boolean selectedRowEvidence) {
        return GtoFreightReviewPolicy.isManualValueValid(GtoFreightReviewPolicy.VALUE, value)
            && (votes >= 2 || selectedRowEvidence);
    }

    static boolean optionalMetadata(String value, int votes, boolean selectedRowEvidence) {
        if (value == null || value.trim().isEmpty()) return true;
        return validText(value) && (votes >= 1 || selectedRowEvidence);
    }

    static float requiredConfidence(
        boolean cargo,
        boolean origin,
        boolean destination,
        boolean distance,
        boolean value
    ) {
        int ok = 0;
        if (cargo) ok++;
        if (origin) ok++;
        if (destination) ok++;
        if (distance) ok++;
        if (value) ok++;
        return ok / 5f;
    }

    private static boolean validText(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (v.length() < 2 || v.length() > 220) return false;
        int letters = 0;
        for (int i = 0; i < v.length(); i++) if (Character.isLetter(v.charAt(i))) letters++;
        return letters >= 2;
    }
}
