package dev.kardane.autobattle.config;

import dev.kardane.autobattle.AutoBattleConstants;

public record AutoBattleConfig(
    int minimumPlayers,
    int roundCount,
    int roundDurationTicks
) {
    public AutoBattleConfig {
        if (minimumPlayers < 2) {
            throw new IllegalArgumentException("minimumPlayers must be at least 2");
        }
        if (roundCount < 1) {
            throw new IllegalArgumentException("roundCount must be positive");
        }
        if (roundDurationTicks < 20) {
            throw new IllegalArgumentException("roundDurationTicks must be at least 20");
        }
    }

    public static AutoBattleConfig defaults() {
        return new AutoBattleConfig(
            AutoBattleConstants.DEFAULT_MIN_PLAYERS,
            AutoBattleConstants.DEFAULT_ROUNDS,
            AutoBattleConstants.ROUND_DURATION_TICKS
        );
    }
}
