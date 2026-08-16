package com.nvu.operacional;

/**
 * A visual orange/card candidate becomes a certified freight list only when at least one
 * detected row also carries a real monetary freight value. Geometry finds candidates;
 * semantics authorizes freight-list state/selection.
 */
final class GtoFreightSemanticCertificationPolicy {
    private GtoFreightSemanticCertificationPolicy() {}

    static boolean isCertifiedPage(int visualRowCount, int parsedRowCount, int sameRowMoneyAnchors) {
        if (visualRowCount < 1 || visualRowCount > 6) return false;
        if (parsedRowCount < 1 || parsedRowCount > 6) return false;
        return sameRowMoneyAnchors >= 1;
    }

    static boolean selectedRowCanCertify(
        boolean hasAcceptGeometry,
        boolean hasAcceptTextEvidence,
        String cargo,
        String origin,
        String destination,
        String distance,
        String value
    ) {
        if (!hasAcceptGeometry || !hasAcceptTextEvidence) return false;
        boolean money = GtoFreightReviewPolicy.isManualValueValid(GtoFreightReviewPolicy.VALUE, value);
        if (!money) return false;
        int contextual = 0;
        if (GtoFreightReviewPolicy.isAutomaticTextUsable(cargo)) contextual++;
        if (GtoFreightReviewPolicy.isAutomaticTextUsable(origin)) contextual++;
        if (GtoFreightReviewPolicy.isAutomaticTextUsable(destination)) contextual++;
        if (GtoFreightReviewPolicy.isManualValueValid(GtoFreightReviewPolicy.DISTANCE, distance)) contextual++;
        return contextual >= 1;
    }
    static boolean selectedRowCanCertify(
        boolean hasAcceptGeometry,
        String cargo,
        String origin,
        String destination,
        String distance,
        String value
    ) {
        return selectedRowCanCertify(
            hasAcceptGeometry, true, cargo, origin, destination, distance, value
        );
    }

}
