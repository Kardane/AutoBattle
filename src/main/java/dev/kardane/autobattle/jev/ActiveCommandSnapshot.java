package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.command.PlayerCommandType;

public record ActiveCommandSnapshot(
    PlayerCommandType type,
    long remainingTicks
) {
}
