package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.robot.RobotColor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DecisionLog(
    int schemaVersion,
    String decisionMode,
    String modVersion,
    DecisionTrigger trigger,
    UUID matchId,
    int round,
    long serverTick,
    long observedTick,
    UUID ownerUuid,
    UUID robotEntityUuid,
    RobotColor color,
    String targetId,
    BattleTeam team,
    int doctrineVersion,
    List<String> requestValidPlanIds,
    List<String> applyValidPlanIds,
    boolean candidateSetChanged,
    String previousPlanId,
    ChoiceDecision strategicIntent,
    ChoiceDecision combatTarget,
    ChoiceDecision pursuitStyle,
    String composedPlanId,
    String effectivePlanId,
    long latencyMs,
    boolean fallback,
    DecisionApplyResult applyResult,
    float hp,
    float maxHp,
    double hpRatio,
    int teamScore,
    int enemyTeamScore,
    int aliveAllies,
    int aliveEnemies,
    int alliesInsideCore,
    int enemiesInsideCore,
    BattleTeam coreOwnerTeam,
    boolean coreContested,
    String errorClass,
    String errorMessage,
    Integer httpStatus,
    DecisionPosition robotPosition,
    DecisionPosition targetPosition,
    DecisionPosition destination,
    Double distanceToCore,
    Double distanceToTarget
) {
    public DecisionLog {
        requestValidPlanIds = List.copyOf(
            requestValidPlanIds
        );
        applyValidPlanIds = List.copyOf(
            applyValidPlanIds
        );
    }

    /**
     * Compatibility accessor for round-review code.
     */
    public String selectedPlanId() {
        return composedPlanId;
    }

    /**
     * The high-level intent confidence remains the primary confidence
     * used by the existing round-review UI.
     */
    public double confidence() {
        return strategicIntent == null
            ? 0.0D
            : strategicIntent.confidence();
    }

    /**
     * Compatibility accessor for round-review code.
     */
    public Map<String, Double> probabilities() {
        return strategicIntent == null
            ? Map.of()
            : strategicIntent.probabilities();
    }

    /**
     * Compatibility accessor for historical naming.
     */
    public List<String> validPlanIds() {
        return requestValidPlanIds;
    }
}
