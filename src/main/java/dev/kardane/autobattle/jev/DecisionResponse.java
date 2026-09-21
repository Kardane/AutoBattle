package dev.kardane.autobattle.jev;

public record DecisionResponse(
    ChoiceDecision strategicIntent,
    ChoiceDecision combatTarget,
    ChoiceDecision pursuitStyle,
    long latencyMs
) {
}
