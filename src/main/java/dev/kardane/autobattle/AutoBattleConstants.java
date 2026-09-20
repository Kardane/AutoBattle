package dev.kardane.autobattle;

public final class AutoBattleConstants {
    public static final int DEFAULT_MIN_PLAYERS = 4;
    public static final int DEFAULT_ROUNDS = 5;
    public static final int ROUND_DURATION_TICKS = 20 * 90;
    public static final int ROBOT_RESPAWN_TICKS = 20 * 7;
    public static final int DECISION_INTERVAL_TICKS = 20 * 3;
    public static final int DECISION_LOCK_TICKS = 20 * 2;
    public static final int DECISION_DEBOUNCE_TICKS = 10;
    public static final int COMMAND_DURATION_TICKS = 20 * 10;
    public static final int REGEN_DELAY_TICKS = 20 * 5;
    public static final int REGEN_INTERVAL_TICKS = 20;
    public static final float REGEN_AMOUNT = 8.0F;

    private AutoBattleConstants() {
    }
}
