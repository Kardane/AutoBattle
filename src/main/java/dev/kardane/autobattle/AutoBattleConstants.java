package dev.kardane.autobattle;

public final class AutoBattleConstants {
    public static final int DEFAULT_MIN_PLAYERS = 4;
    public static final int DEFAULT_ROUNDS = 5;

    public static final int ROUND_DURATION_TICKS = 20 * 90;
    public static final int COUNTDOWN_TICKS = 20 * 5;
    public static final int ROBOT_RESPAWN_TICKS = 20 * 7;

    public static final int DECISION_INTERVAL_TICKS = 20 * 3;
    public static final int DECISION_LOCK_TICKS = 20 * 2;
    public static final int DECISION_DEBOUNCE_TICKS = 10;
    public static final int JEV_TIMEOUT_MS = 1500;

    public static final int COMMAND_DURATION_TICKS = 20 * 10;

    public static final int REGEN_DELAY_TICKS = 20 * 5;
    public static final int REGEN_INTERVAL_TICKS = 20;
    public static final float REGEN_AMOUNT = 8.0F;

    public static final double ENGAGE_LEASH_DISTANCE = 8.0D;
    public static final double CHASE_LEASH_DISTANCE = 20.0D;

    public static final double CORE_RADIUS = 3.0D;
    public static final int CORE_CAPTURE_TICKS = 20 * 3;
    public static final int CORE_HOLD_SCORE_INTERVAL = 20 * 2;

    public static final int KILL_SCORE = 5;
    public static final int ASSIST_SCORE = 2;
    public static final int CORE_CAPTURE_SCORE = 3;
    public static final int CORE_HOLD_SCORE = 1;
    public static final int ASSIST_WINDOW_TICKS = 20 * 5;

    private AutoBattleConstants() {
    }
}
