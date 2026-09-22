package dev.kardane.autobattle.jev;

public enum DecisionTrigger {
    INITIAL,
    INTERVAL,
    DAMAGE,
    PLAYER_COMMAND,
    TARGET_INVALIDATED,
    PLAN_COMPLETED,
    MOVEMENT_FAILED,
    RESPAWN,
    STALE_RETRY,
    OTHER
}
