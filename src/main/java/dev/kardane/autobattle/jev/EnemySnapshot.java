package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.robot.RobotColor;

import java.util.UUID;

public record EnemySnapshot(
    UUID ownerUuid,
    RobotColor color,
    boolean alive,
    float hp,
    double distance,
    int rank,
    int roundScore,
    boolean attackingSelf,
    int killsAgainstSelfThisRound
) {
}
