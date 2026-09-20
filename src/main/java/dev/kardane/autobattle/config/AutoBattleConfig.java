package dev.kardane.autobattle.config;

import dev.kardane.autobattle.AutoBattleConstants;
import dev.kardane.autobattle.jev.TypeSafeJevClient;

import java.util.Objects;

public record AutoBattleConfig(
    TypeSafeConfig typesafe,
    MatchRulesConfig match,
    AiConfig ai,
    DoctrineConfig doctrine,
    DoctrineNormalizerConfig doctrineNormalizer,
    RobotConfig robot,
    ScoringConfig scoring,
    CoreRulesConfig core,
    ArenaConfig arena
) {
    public AutoBattleConfig {
        Objects.requireNonNull(typesafe, "typesafe");
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(ai, "ai");
        Objects.requireNonNull(doctrine, "doctrine");
        Objects.requireNonNull(
            doctrineNormalizer,
            "doctrineNormalizer"
        );
        Objects.requireNonNull(robot, "robot");
        Objects.requireNonNull(scoring, "scoring");
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(arena, "arena");
    }

    public int minimumPlayers() {
        return match.minimumPlayers();
    }

    public int roundCount() {
        return match.roundCount();
    }

    public int roundDurationTicks() {
        return match.roundDurationTicks();
    }

    public int countdownTicks() {
        return match.countdownTicks();
    }

    public int respawnTicks() {
        return match.respawnTicks();
    }

    public int commandDurationTicks() {
        return match.commandDurationTicks();
    }

    public int decisionIntervalTicks() {
        return ai.decisionIntervalTicks();
    }

    public int decisionLockTicks() {
        return ai.decisionLockTicks();
    }

    public int decisionDebounceTicks() {
        return ai.decisionDebounceTicks();
    }

    public int jevTimeoutMs() {
        return ai.requestTimeoutMs();
    }

    public static AutoBattleConfig defaults() {
        return new AutoBattleConfig(
            new TypeSafeConfig(
                "",
                TypeSafeJevClient.DEFAULT_BASE_URL,
                TypeSafeJevClient.DEFAULT_MODEL
            ),
            new MatchRulesConfig(
                AutoBattleConstants.DEFAULT_MIN_PLAYERS,
                AutoBattleConstants.DEFAULT_ROUNDS,
                AutoBattleConstants.ROUND_DURATION_TICKS,
                AutoBattleConstants.COUNTDOWN_TICKS,
                AutoBattleConstants.ROBOT_RESPAWN_TICKS,
                AutoBattleConstants.COMMAND_DURATION_TICKS
            ),
            new AiConfig(
                AutoBattleConstants.DECISION_INTERVAL_TICKS,
                AutoBattleConstants.DECISION_LOCK_TICKS,
                AutoBattleConstants.DECISION_DEBOUNCE_TICKS,
                AutoBattleConstants.JEV_TIMEOUT_MS,
                AutoBattleConstants.JEV_MIN_CONFIDENCE,
                AutoBattleConstants.JEV_FALLBACK_RETREAT_HP_RATIO
            ),
            new DoctrineConfig(
                AutoBattleConstants.DOCTRINE_MAX_LINE_LENGTH
            ),
            new DoctrineNormalizerConfig(
                true,
                "",
                "https://api.openai.com",
                "gpt-5.6-luna",
                2500
            ),
            new RobotConfig(
                AutoBattleConstants.ROBOT_MAX_HEALTH,
                AutoBattleConstants.ROBOT_ATTACK_DAMAGE,
                AutoBattleConstants.ROBOT_MOVEMENT_SPEED,
                AutoBattleConstants.ROBOT_FOLLOW_RANGE,
                AutoBattleConstants.REGEN_DELAY_TICKS,
                AutoBattleConstants.REGEN_INTERVAL_TICKS,
                AutoBattleConstants.REGEN_AMOUNT,
                AutoBattleConstants.ENGAGE_LEASH_DISTANCE,
                AutoBattleConstants.CHASE_LEASH_DISTANCE,
                AutoBattleConstants.ENGAGE_SPEED,
                AutoBattleConstants.CHASE_SPEED,
                AutoBattleConstants.CAPTURE_SPEED,
                AutoBattleConstants.DEFEND_SPEED,
                AutoBattleConstants.REPOSITION_SPEED,
                AutoBattleConstants.RETREAT_SPEED,
                AutoBattleConstants.POSITION_REACHED_DISTANCE,
                AutoBattleConstants.DEFEND_RADIUS,
                AutoBattleConstants.RETREAT_DISTANCE
            ),
            new ScoringConfig(
                AutoBattleConstants.KILL_SCORE,
                AutoBattleConstants.ASSIST_SCORE,
                AutoBattleConstants.CORE_CAPTURE_SCORE,
                AutoBattleConstants.CORE_HOLD_SCORE,
                AutoBattleConstants.ASSIST_WINDOW_TICKS
            ),
            new CoreRulesConfig(
                AutoBattleConstants.CORE_CAPTURE_TICKS,
                AutoBattleConstants.CORE_HOLD_SCORE_INTERVAL
            ),
            ArenaConfig.defaults()
        );
    }
}
