package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.command.PlayerCommandType;

import java.util.List;
import java.util.Objects;

public final class DecisionComposer {
    private static final double HOLD_MINIMUM_CONFIDENCE = 0.60D;

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
                        : currentPlanIds.contains("HOLD_POSITION")
                            ? "HOLD_POSITION"
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
                containsPlan(currentPlanIds, currentPlanId)
                    && !isHoldPlan(currentPlanId)
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
            case "SUPPORT" -> composeSupport(
                response,
                currentPlanIds,
                currentPlanId,
                minimumConfidence
            );
            case "HOLD" -> composeHold(
                currentPlanIds,
                currentPlanId,
                intent.confidence(),
                minimumConfidence
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

    private DecisionComposition composeHold(
        List<String> currentPlanIds,
        String currentPlanId,
        double confidence,
        double minimumConfidence
    ) {
        double requiredConfidence = Math.max(
            minimumConfidence,
            HOLD_MINIMUM_CONFIDENCE
        );

        if (confidence < requiredConfidence) {
            return new DecisionComposition(
                containsPlan(currentPlanIds, currentPlanId)
                    && !isHoldPlan(currentPlanId)
                    ? currentPlanId
                    : null,
                true,
                false
            );
        }

        return new DecisionComposition(
            currentPlanIds.contains("HOLD_POSITION")
                ? "HOLD_POSITION"
                : null,
            false,
            false
        );
    }

    private DecisionComposition composeSupport(
        DecisionResponse response,
        List<String> currentPlanIds,
        String currentPlanId,
        double minimumConfidence
    ) {
        ChoiceDecision allyTarget = response.allyTarget();

        if (allyTarget == null) {
            String soleTarget = currentPlanIds.stream()
                .filter(planId -> planId.startsWith("ASSIST_"))
                .findFirst()
                .orElse(null);

            boolean onlyOneSupportTarget = currentPlanIds
                .stream()
                .filter(planId -> planId.startsWith("ASSIST_"))
                .count() == 1L;

            if (onlyOneSupportTarget) {
                return new DecisionComposition(
                    soleTarget,
                    false,
                    false
                );
            }
        }

        if (allyTarget == null
            || allyTarget.confidence() < minimumConfidence) {
            if (isAssistPlan(currentPlanId)
                && containsPlan(currentPlanIds, currentPlanId)) {
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

        String choice = allyTarget.choice();
        String planId = choice.startsWith("ASSIST_")
            ? choice
            : "ASSIST_" + choice;

        if (currentPlanIds.contains(planId)) {
            return new DecisionComposition(
                planId,
                false,
                false
            );
        }

        return new DecisionComposition(
            null,
            false,
            true
        );
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
                && containsPlan(currentPlanIds, currentPlanId)) {
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

    private boolean containsPlan(List<String> planIds, String planId) {
        return planId != null && planIds.contains(planId);
    }

    private boolean isCombatPlan(String planId) {
        return planId != null
            && (planId.startsWith("ENGAGE_")
                || planId.startsWith("CHASE_"));
    }

    private boolean isAssistPlan(String planId) {
        return planId != null
            && planId.startsWith("ASSIST_");
    }

    private boolean isHoldPlan(String planId) {
        return "HOLD_POSITION".equals(planId);
    }
}
