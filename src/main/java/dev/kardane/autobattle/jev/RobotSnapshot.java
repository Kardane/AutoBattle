package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.robot.RobotColor;

import java.util.UUID;

public record RobotSnapshot(
    UUID ownerUuid,
    RobotColor color,
    float hp,
    float maxHp,
    int roundScore,
    int totalScore,
    int rank,
    String currentPlan
) {
}
