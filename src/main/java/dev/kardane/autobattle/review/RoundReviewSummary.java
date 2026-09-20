package dev.kardane.autobattle.review;

import java.util.List;
import java.util.Map;

public record RoundReviewSummary(
    int round,
    int roundScore,
    int kills,
    int deaths,
    int assists,
    int coreCaptures,
    long coreHoldTicks,
    float damageDealt,
    float damageTaken,
    Map<String, Integer> planCounts,
    Map<String, Double> planPercentages,
    List<CriticalDecision> criticalDecisions
) {
    public RoundReviewSummary {
        planCounts = Map.copyOf(planCounts);
        planPercentages = Map.copyOf(planPercentages);
        criticalDecisions = List.copyOf(criticalDecisions);
    }
}
