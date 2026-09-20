package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.doctrine.Doctrine;

import java.util.List;
import java.util.UUID;

public record RobotDecisionSnapshot(
    UUID matchId,
    int round,
    long serverTick,
    int remainingRoundSeconds,
    RobotSnapshot self,
    CoreSnapshot core,
    List<EnemySnapshot> enemies,
    Doctrine doctrine,
    ActiveCommandSnapshot command
) {
    public RobotDecisionSnapshot {
        enemies = List.copyOf(enemies);
    }
}
