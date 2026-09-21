package dev.kardane.autobattle.review;

import dev.kardane.autobattle.jev.DecisionApplyResult;
import dev.kardane.autobattle.jev.DecisionLog;
import dev.kardane.autobattle.jev.DecisionLogRepository;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RoundReviewService {
    private final DecisionLogRepository logs;

    public RoundReviewService(
        DecisionLogRepository logs
    ) {
        this.logs = logs;
    }

    public RoundReviewSummary build(
        MatchSession match,
        UUID ownerUuid
    ) {
        PlayerSlot slot = match.player(ownerUuid)
            .orElseThrow();

        List<DecisionLog> decisions = logs.findRound(
            match.matchId(),
            match.currentRound(),
            ownerUuid
        );

        Map<String, Integer> counts =
            new LinkedHashMap<>();

        for (DecisionLog decision : decisions) {
            String selected = decision.selectedPlanId();

            if (selected == null) {
                continue;
            }

            String family = planFamily(selected);

            counts.merge(
                family,
                1,
                Integer::sum
            );
        }

        int totalSelections = counts.values()
            .stream()
            .mapToInt(Integer::intValue)
            .sum();

        Map<String, Double> percentages =
            new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry :
            counts.entrySet()) {
            double percent = totalSelections == 0
                ? 0.0D
                : entry.getValue() * 100.0D
                    / totalSelections;

            percentages.put(entry.getKey(), percent);
        }

        List<CriticalDecision> critical =
            decisions.stream()
                .map(decision ->
                    new CriticalDecision(
                        decision,
                        importance(decision)
                    )
                )
                .filter(item -> item.importanceScore() > 0)
                .sorted(
                    Comparator
                        .comparingInt(
                            CriticalDecision::importanceScore
                        )
                        .reversed()
                        .thenComparingLong(item ->
                            item.decision().serverTick()
                        )
                )
                .limit(3)
                .toList();

        var score = slot.score();

        return new RoundReviewSummary(
            match.currentRound(),
            score.roundScore(),
            score.roundKills(),
            score.roundDeaths(),
            score.roundAssists(),
            score.roundCoreCaptures(),
            score.roundCoreHoldTicks(),
            score.roundDamageDealt(),
            score.roundDamageTaken(),
            counts,
            percentages,
            critical
        );
    }

    private int importance(DecisionLog log) {
        int score = 0;

        if (log.fallback()) {
            score += 5;
        }

        if (log.applyResult() != DecisionApplyResult.APPLIED
            && log.applyResult()
                != DecisionApplyResult.APPLIED_RECOMPOSED) {
            score += 2;
        }

        if (log.confidence() < 0.50D) {
            score += 4;
        } else if (log.confidence() < 0.70D) {
            score += 2;
        }

        List<Double> probabilities =
            new ArrayList<>(
                log.probabilities().values()
            );

        probabilities.sort(Comparator.reverseOrder());

        if (probabilities.size() >= 2) {
            double margin =
                probabilities.get(0) - probabilities.get(1);

            if (margin < 0.10D) {
                score += 3;
            } else if (margin < 0.20D) {
                score += 1;
            }
        }

        return score;
    }

    private String planFamily(String planId) {
        if (planId.startsWith("ENGAGE_")) {
            return "ENGAGE";
        }

        if (planId.startsWith("CHASE_")) {
            return "CHASE";
        }

        if (planId.startsWith("CAPTURE_")) {
            return "CAPTURE";
        }

        if (planId.startsWith("DEFEND_")) {
            return "DEFEND";
        }

        return planId;
    }
}
