package dev.kardane.autobattle.config;

import dev.kardane.autobattle.AutoBattleConstants;

import java.util.Objects;

public record AutoBattleConfig(
    int minimumPlayers,
    int roundCount,
    int roundDurationTicks,
    int respawnTicks,
    int decisionIntervalTicks,
    int decisionLockTicks,
    int decisionDebounceTicks,
    int commandDurationTicks,
    int jevTimeoutMs,
    ArenaConfig arena
) {
    public AutoBattleConfig {
        if (minimumPlayers < 2) {
            throw new IllegalArgumentException(
                "minimumPlayers must be at least 2"
            );
        }

        if (roundCount < 1) {
            throw new IllegalArgumentException(
                "roundCount must be positive"
            );
        }

        if (roundDurationTicks < 20) {
            throw new IllegalArgumentException(
                "roundDurationTicks must be at least 20"
            );
        }

        if (respawnTicks < 1
            || decisionIntervalTicks < 1
            || decisionLockTicks < 0
            || decisionDebounceTicks < 0
            || commandDurationTicks < 1
            || jevTimeoutMs < 1) {
            throw new IllegalArgumentException(
                "AutoBattle tick configuration is invalid"
            );
        }

        Objects.requireNonNull(arena, "arena");
    }

    public static AutoBattleConfig defaults() {
        return new AutoBattleConfig(
            AutoBattleConstants.DEFAULT_MIN_PLAYERS,
            AutoBattleConstants.DEFAULT_ROUNDS,
            AutoBattleConstants.ROUND_DURATION_TICKS,
            AutoBattleConstants.ROBOT_RESPAWN_TICKS,
            AutoBattleConstants.DECISION_INTERVAL_TICKS,
            AutoBattleConstants.DECISION_LOCK_TICKS,
            AutoBattleConstants.DECISION_DEBOUNCE_TICKS,
            AutoBattleConstants.COMMAND_DURATION_TICKS,
            AutoBattleConstants.JEV_TIMEOUT_MS,
            ArenaConfig.defaults()
        );
    }
}
