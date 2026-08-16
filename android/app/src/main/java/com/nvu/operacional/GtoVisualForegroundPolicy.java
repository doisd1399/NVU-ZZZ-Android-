package com.nvu.operacional;

/**
 * Conservative policy for using a live GTO freight-list capture as foreground proof.
 *
 * A real list may bridge an OEM that fails to report GTO after MediaProjection returns,
 * but it may never override a positively identified non-GTO foreground application.
 * This keeps notification shade, Recents and other apps neutral to the trip state.
 */
final class GtoVisualForegroundPolicy {
    private GtoVisualForegroundPolicy() {}

    static boolean allowFreightListProof(
        boolean waitingForFreight,
        boolean projectionActive,
        boolean packageMatchesGto,
        boolean packageUnknown,
        boolean permissionReturnFromNvu,
        int freightCount
    ) {
        return GtoDeterministicFlowPolicy.mayUseVisualFreightProof(
            waitingForFreight,
            projectionActive,
            packageMatchesGto,
            packageUnknown,
            permissionReturnFromNvu,
            freightCount
        );
    }
}
