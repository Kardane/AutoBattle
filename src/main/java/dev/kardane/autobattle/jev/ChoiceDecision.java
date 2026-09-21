package dev.kardane.autobattle.jev;

import java.util.Map;
import java.util.Objects;

public record ChoiceDecision(
    String choice,
    double confidence,
    Map<String, Double> probabilities
) {
    public ChoiceDecision {
        choice = Objects.requireNonNull(choice, "choice");
        probabilities = probabilities == null
            ? Map.of()
            : Map.copyOf(probabilities);
    }
}
