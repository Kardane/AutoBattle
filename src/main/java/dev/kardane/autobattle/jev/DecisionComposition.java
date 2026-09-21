package dev.kardane.autobattle.jev;

public record DecisionComposition(
    String planId,
    boolean lowConfidence,
    boolean targetUnavailable
) {
}
