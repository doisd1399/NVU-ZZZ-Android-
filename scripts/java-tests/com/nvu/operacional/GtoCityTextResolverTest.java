package com.nvu.operacional;

import java.util.Arrays;
import java.util.Collections;

public final class GtoCityTextResolverTest {
    public static void main(String[] args) {
        requireLiteral("Itopetuna");
        requireLiteral("Nova Macae");
        requireLiteral("Aguas Velhas");
        requireLiteral("Registro");
        requireLiteral("Faz Areia Dourad");
        requireLiteral("Cruz do Oest");
        requireLiteral("Cooperativa Agro Grao");
        requireLiteral("Curitba");
        requireLiteral("Lages");
        requireLiteral("Lauro Muler");

        GtoCityTextResolver.Resolution advisory = GtoCityTextResolver.resolveTrusted(
            "Itopetuna", "Itapetuna", Arrays.asList("Itepetuna", "São Paulo")
        );
        require(!advisory.corrected && "Itopetuna".equals(advisory.value),
            "known/expected destinations are advisory only; OCR literal must not be rewritten");

        GtoCityTextResolver.Resolution unrelated = GtoCityTextResolver.resolveTrusted(
            "Cuiabá", "", Arrays.asList("São Paulo")
        );
        require(!unrelated.corrected && "Cuiabá".equals(unrelated.value),
            "unrelated OCR text must remain literal");

        // Exact selected-row OCR may still choose a stronger literal observation. This is
        // evidence selection, not dictionary/city autocorrection, and the runtime currently
        // does not call this helper for destination canonicalization.
        GtoCityTextResolver.Resolution precise = GtoCityTextResolver.preferPreciseVerifiedRow(
            "Cidade Alfa", 0.79f, "Cidade Alga", 0.91f
        );
        require(precise.corrected && "Cidade Alga".equals(precise.value),
            "higher-confidence exact-row OCR evidence may beat weaker page OCR");

        GtoCityTextResolver.Resolution weakPrecise = GtoCityTextResolver.preferPreciseVerifiedRow(
            "Cidade Alfa", 0.88f, "Cidade Alga", 0.89f
        );
        require(!weakPrecise.corrected,
            "nearly equal confidence must preserve the existing literal observation");

        require(!GtoCityTextResolver.isSafeOneEditVariant("Metalurgica", "Itapetuna"),
            "company names must never be treated as destination corrections");

        System.out.println("GtoCityTextResolverTest: PASS");
    }

    private static void requireLiteral(String ocr) {
        GtoCityTextResolver.Resolution resolution = GtoCityTextResolver.resolveTrusted(
            ocr, "", Collections.emptyList()
        );
        require(ocr.equals(resolution.value), "literal mismatch for " + ocr + ": " + resolution.value);
        require(!resolution.corrected, "literal OCR must never be marked as autocorrected: " + ocr);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
