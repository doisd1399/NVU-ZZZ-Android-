package com.nvu.operacional;

/** Multiple-evidence wake-up policy for the GTO completion/result dialog. */
final class GtoResultEvidencePolicy {
    private GtoResultEvidencePolicy() {}

    static boolean isPlausibleResult(
        float dialogDark,
        float dialogRightDark,
        float receiveNeutral,
        float adsGold
    ) {
        // The central modal remains the anchor. Any one supporting region may be partly
        // obscured without suppressing result OCR; two independent supports are required.
        if (dialogDark < 0.58f) return false;
        int supports = 0;
        if (dialogRightDark >= 0.50f) supports++;
        if (receiveNeutral >= 0.30f) supports++;
        if (adsGold >= 0.10f) supports++;
        return supports >= 2;
    }
}
