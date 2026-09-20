package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.robot.RobotColor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DecisionLog(
    UUID matchId,
    int round,
    long serverTick,
    UUID ownerUuid,
    UUID robotEntityUuid,
    RobotColor color,
    int doctrineVersion,
    List<String> validPlanIds,
    String selectedPlanId,
    double confidence,
    Map<String, Double> probabilities,
    long latencyMs,
    boolean fallback,
    DecisionApplyResult applyResult
) {
    public DecisionLog {
        validPlanIds = List.copyOf(validPlanIds);
        probabilities = probabilities == null
            ? Map.of()
            : Map.copyOf(probabilities);
    }
}
