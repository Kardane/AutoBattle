package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.match.BattleTeam;

import java.util.UUID;

public record AllySnapshot(
    UUID ownerUuid,
    String targetId,
    BattleTeam team,
    boolean alive,
    float hp,
    float maxHp,
    double hpRatio,
    Double distance,
    String currentPlan,
    String combatTarget,
    boolean insideCore,
    boolean underAttack
) {
}
