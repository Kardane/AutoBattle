package dev.kardane.autobattle.command;

import java.util.Objects;

public record ActiveCommand(
    PlayerCommandType type,
    long activatedTick,
    long expiresAtTick
) {
    public ActiveCommand {
        Objects.requireNonNull(type, "type");

        if (activatedTick < 0L) {
            throw new IllegalArgumentException(
                "activatedTick must not be negative"
            );
        }

        if (expiresAtTick <= activatedTick) {
            throw new IllegalArgumentException(
                "expiresAtTick must be after activatedTick"
            );
        }
    }

    public boolean active(long currentTick) {
        return currentTick < expiresAtTick;
    }

    public long remainingTicks(long currentTick) {
        return Math.max(0L, expiresAtTick - currentTick);
    }
}
