package dev.kardane.autobattle.config;

public record MatchRulesConfig(
    int minimumPlayers,
    int roundCount,
    int roundDurationTicks,
    int countdownTicks,
    int respawnTicks,
    int commandDurationTicks
) {
    public MatchRulesConfig {
        if (minimumPlayers < 2 || minimumPlayers > 4) {
            throw new IllegalArgumentException(
                "minimumPlayers must be between 2 and 4"
            );
        }

        if (roundCount < 1
            || roundDurationTicks < 20
            || countdownTicks < 0
            || respawnTicks < 1
            || commandDurationTicks < 1) {
            throw new IllegalArgumentException(
                "Invalid match timing configuration"
            );
        }
    }
}
