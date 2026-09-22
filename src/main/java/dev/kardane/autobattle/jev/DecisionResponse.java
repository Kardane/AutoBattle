package dev.kardane.autobattle.jev;

public record DecisionResponse(
    ChoiceDecision strategicIntent,
    ChoiceDecision combatTarget,
    ChoiceDecision pursuitStyle,
    ChoiceDecision allyTarget,
    long latencyMs
) {
    public DecisionResponse(
        ChoiceDecision strategicIntent,
        ChoiceDecision combatTarget,
        ChoiceDecision pursuitStyle,
        long latencyMs
    ) {
        this(
            strategicIntent,
            combatTarget,
            pursuitStyle,
            null,
            latencyMs
        );
    }
}
