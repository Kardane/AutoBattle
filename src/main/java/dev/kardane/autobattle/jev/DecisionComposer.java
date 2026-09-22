package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.command.PlayerCommandType;

import java.util.List;
import java.util.Objects;

public final class DecisionComposer {
    public DecisionComposition compose(
        DecisionResponse response,
        List<String> currentPlanIds,
        String currentPlanId,
        double minimumConfidence
    ) {
        return compose(
            response,
            currentPlanIds,
            currentPlanId,
            minimumConfidence,
            null
        );
    }

    public DecisionComposition compose(
        DecisionResponse response,
        List<String> currentPlanIds,
        String currentPlanId,
        double minimumConfidence,
        PlayerCommandType commandType
    ) {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(currentPlanIds, "currentPlanIds");

        if (commandType != null) {
            return switch (commandType) {
                case ATTACK -> composeFight(
                    response,
                    currentPlanIds,
                    currentPlanId,
                    minimumConfidence
                );
                case CAPTURE -> new DecisionComposition(
                    objectivePlan(currentPlanIds),
                    false,
                    false
                );
                case SURVIVE -> new DecisionComposition(
                    currentPlanIds.contains("RETREAT")
                        ? "RETREAT"
                        : null,
                    false,
                    false
                );
            };
        }

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
                false
            );
        }

        String targetId = target.choice();
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

        String planId = style + "_" + targetId;

        if (currentPlanIds.contains(planId)) {
            return new DecisionComposition(
                planId,
                lowConfidence,
                false
            );
        }

        String engagePlan = "ENGAGE_" + targetId;

        if ("CHASE".equals(style)
            && currentPlanIds.contains(engagePlan)) {
            return new DecisionComposition(
                engagePlan,
                true,
                false
            );
        }

        boolean targetStillLegal =
            currentPlanIds.contains(
                "ENGAGE_" + targetId
            ) || currentPlanIds.contains(
                "CHASE_" + targetId
            );

        return new DecisionComposition(
            null,
            lowConfidence,
            !targetStillLegal
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
