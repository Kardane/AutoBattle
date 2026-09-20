package dev.kardane.autobattle.jev;

import java.util.Map;

public record DecisionResponse(
    String selectedPlanId,
    double confidence,
    Map<String, Double> probabilities,
    long latencyMs
) {
    public DecisionResponse {
        probabilities = probabilities == null
            ? Map.of()
            : Map.copyOf(probabilities);
    }
}
