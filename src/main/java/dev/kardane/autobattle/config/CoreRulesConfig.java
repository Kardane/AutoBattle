package dev.kardane.autobattle.config;

public record CoreRulesConfig(
    int captureTicks,
    int holdScoreIntervalTicks
) {
    public CoreRulesConfig {
        if (captureTicks < 1
            || holdScoreIntervalTicks < 1) {
            throw new IllegalArgumentException(
                "Invalid CORE timing configuration"
            );
        }
    }
}
