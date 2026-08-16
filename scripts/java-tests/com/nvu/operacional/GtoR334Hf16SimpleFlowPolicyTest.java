package com.nvu.operacional;

public final class GtoR334Hf16SimpleFlowPolicyTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public static void main(String[] args) {
        require(GtoSimpleScreenDetectionPolicy.isCompletedResult(true, true),
            "Concluido + valor deve detectar resultado");
        require(!GtoSimpleScreenDetectionPolicy.isCompletedResult(true, false),
            "Concluido sem valor nao deve detectar resultado");
        require(!GtoSimpleScreenDetectionPolicy.isCompletedResult(false, true),
            "Valor isolado nao deve detectar resultado");

        require(GtoSimpleScreenDetectionPolicy.isStableFreightListReturn(
            "TRIP_IN_PROGRESS", true, 2, 180L),
            "lista reaberta estavel deve ser reconhecida durante viagem");
        require(!GtoSimpleScreenDetectionPolicy.isStableFreightListReturn(
            "TRIP_IN_PROGRESS", true, 1, 180L),
            "um quadro isolado nao deve substituir contexto de viagem");
        require(!GtoSimpleScreenDetectionPolicy.isStableFreightListReturn(
            "RESULT_DETECTED", true, 3, 500L),
            "resultado detectado nao pode ser cancelado por lista");

        require(!GtoSimpleScreenDetectionPolicy.mayReplaceCancelledTripOnNewAccept(
            "TRIP_IN_PROGRESS", true, true, false, false),
            "lista sozinha nunca pode descartar frete anterior");
        require(GtoSimpleScreenDetectionPolicy.mayReplaceCancelledTripOnNewAccept(
            "TRIP_IN_PROGRESS", true, true, true, false),
            "lista estavel + toque deve permitir novo frete");
        require(GtoSimpleScreenDetectionPolicy.mayReplaceCancelledTripOnNewAccept(
            "TRIP_IN_PROGRESS", true, false, true, true),
            "toque exato no Aceitar pode substituir mesmo antes do segundo quadro");
        require(!GtoSimpleScreenDetectionPolicy.mayReplaceCancelledTripOnNewAccept(
            "TRIP_IN_PROGRESS", false, true, true, true),
            "sem lista candidata nao pode substituir frete");

        require(GtoFreightListEvidencePolicy.isPlausibleSimpleList(1, true, 1),
            "um Aceitar + informacao adjacente deve bastar para lista de uma linha");
        require(GtoFreightListEvidencePolicy.isPlausibleSimpleList(5, true, 1),
            "uma linha forte deve preservar lista mesmo com outras linhas defeituosas");
        require(!GtoFreightListEvidencePolicy.isPlausibleSimpleList(3, true, 0),
            "sem Aceitar + informacao na mesma linha nao deve haver lista");

        System.out.println("PASS HF16 simple screen/cancellation policy");
    }
}
