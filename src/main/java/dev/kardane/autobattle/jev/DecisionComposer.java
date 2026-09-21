package dev.kardane.autobattle.jev;

import java.util.List;
import java.util.Objects;

public final class DecisionComposer {
    public DecisionComposition compose(
        DecisionResponse response,
        List<String> currentPlanIds,
        String currentPlanId,
        double minimumConfidence
    ) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(currentPlanIds, "currentPlanIds");

        ChoiceDecision intent = response.strategicIntent();

        if (intent == null
            || intent.confidence() < minimumConfidence) {
            return new DecisionComposition(
                currentPlanIds.contains(currentPlanId)
                    ? currentPlanId
                    : null,
                true,
                false
            );
        }

        return switch (intent.choice()) {
            case "RETREAT" -> new DecisionComposition(
                currentPlanIds.contains("RETREAT")
                    ? "RETREAT"
                    : null,
                false,
                false
            );
            case "CONTROL_CORE" -> new DecisionComposition(
                objectivePlan(currentPlanIds),
                false,
                false
            );
            case "FIGHT" -> composeFight(
                response,
                currentPlanIds,
                currentPlanId,
                minimumConfidence
            );
            default -> new DecisionComposition(
                null,
                false,
                false
            );
        };
    }

    private DecisionComposition composeFight(
        DecisionResponse response,
        List<String> currentPlanIds,
        String currentPlanId,
        double minimumConfidence
    ) {
        ChoiceDecision target = response.combatTarget();

        if (target == null
            || target.confidence() < minimumConfidence) {
            if (isCombatPlan(currentPlanId)
                && currentPlanIds.contains(currentPlanId)) {
                return new DecisionComposition(
                    currentPlanId,
                    true,
                    false
                );
            }

            return new DecisionComposition(
                null,
                true,
                target != null
            );
        }

        String targetColor = target.choice();
        ChoiceDecision pursuit = response.pursuitStyle();

        String style = "ENGAGE";
        boolean lowConfidence = false;

        if (pursuit != null) {
            if (pursuit.confidence() >= minimumConfidence
                && "CHASE".equals(pursuit.choice())) {
                style = "CHASE";
            } else if (pursuit.confidence()
                < minimumConfidence) {
                lowConfidence = true;
            }
        }

        String planId = style + "_" + targetColor;

        if (currentPlanIds.contains(planId)) {
            return new DecisionComposition(
                planId,
                lowConfidence,
                false
            );
        }

        String engagePlan = "ENGAGE_" + targetColor;

        if ("CHASE".equals(style)
            && currentPlanIds.contains(engagePlan)) {
            return new DecisionComposition(
                engagePlan,
                true,
                false
            );
        }

        return new DecisionComposition(
            null,
            lowConfidence,
            true
        );
    }

    private String objectivePlan(List<String> planIds) {
        if (planIds.contains("DEFEND_CORE")) {
            return "DEFEND_CORE";
        }

        if (planIds.contains("CAPTURE_CORE")) {
            return "CAPTURE_CORE";
        }

        return null;
    }

    private boolean isCombatPlan(String planId) {
        return planId != null
            && (planId.startsWith("ENGAGE_")
                || planId.startsWith("CHASE_"));
    }
}
