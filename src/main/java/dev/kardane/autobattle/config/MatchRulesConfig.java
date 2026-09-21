package dev.kardane.autobattle.config;

public record MatchRulesConfig(
    int minTeamSize,
    int maxTeamSize,
    int roundCount,
    int roundDurationTicks,
    int countdownTicks,
    int respawnTicks,
    int commandDurationTicks
) {
    public MatchRulesConfig {
        if (minTeamSize < 1
            || maxTeamSize < minTeamSize
            || maxTeamSize > 8) {
            throw new IllegalArgumentException(
                "Team size must satisfy 1 <= min <= max <= 8"
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
