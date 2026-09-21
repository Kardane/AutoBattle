package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.robot.RobotColor;

import java.util.UUID;

public record EnemySnapshot(
    UUID ownerUuid,
    String targetId,
    BattleTeam team,
    RobotColor color,
    boolean alive,
    float hp,
    float maxHp,
    double hpRatio,
    Double distance,
    DistanceTrend distanceTrend,
    boolean withinEngageRange,
    boolean withinChaseRange,
    int rank,
    int roundScore,
    boolean attackingSelf
) {
}
