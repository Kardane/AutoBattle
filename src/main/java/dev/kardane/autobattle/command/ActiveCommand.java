package dev.kardane.autobattle.command;

public record ActiveCommand(
    PlayerCommandType type,
    long activatedTick,
    long expiresAtTick
) {
    public boolean active(long currentTick) {
        return currentTick < expiresAtTick;
    }

    public long remainingTicks(long currentTick) {
        return Math.max(0L, expiresAtTick - currentTick);
    }
}
