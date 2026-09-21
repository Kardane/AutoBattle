package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.match.BattleTeam;

public record CoreSnapshot(
    BattleTeam ownerTeam,
    boolean contested,
    double distance
) {
}
