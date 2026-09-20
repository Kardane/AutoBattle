package dev.kardane.autobattle.jev;

import java.util.UUID;

public record CoreSnapshot(
    UUID ownerUuid,
    boolean contested,
    double distance
) {
}
