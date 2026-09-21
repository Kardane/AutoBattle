package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.command.PlayerCommandType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ScriptedJevClient implements JevClient {
    @Override
    public CompletableFuture<DecisionResponse> decide(
        DecisionRequest request
    ) {
        RobotDecisionSnapshot snapshot = request.snapshot();
        List<EnemySnapshot> living = snapshot.enemies()
            .stream()
            .filter(EnemySnapshot::alive)
            .filter(enemy -> {
                String color = enemy.color().name();

                return request.validPlanIds().contains(
                    "ENGAGE_" + color
                ) || request.validPlanIds().contains(
                    "CHASE_" + color
                );
            })
            .toList();

        String intentChoice = chooseIntent(
            request,
            living
        );

        ChoiceDecision intent = deterministic(
            intentChoice,
            availableIntents(request, living)
        );

        ChoiceDecision target = null;
        ChoiceDecision pursuit = null;

        if (!living.isEmpty()) {
            String targetChoice = chooseTarget(living);
            target = deterministic(
                targetChoice,
                living.stream()
                    .map(enemy -> enemy.color().name())
                    .toList()
            );

            List<String> pursuitChoices =
                availablePursuitStyles(request);
            String pursuitChoice = choosePursuit(
                snapshot,
                pursuitChoices
            );
            pursuit = deterministic(
                pursuitChoice,
                pursuitChoices
            );
        }

        return CompletableFuture.completedFuture(
            new DecisionResponse(
                intent,
                target,
                pursuit,
                0L
            )
        );
    }

    private String chooseIntent(
        DecisionRequest request,
        List<EnemySnapshot> living
    ) {
        RobotDecisionSnapshot snapshot = request.snapshot();

        double hpRatio = snapshot.self().maxHp() <= 0.0F
            ? 0.0D
            : snapshot.self().hp() / snapshot.self().maxHp();

        if (hpRatio <= 0.25D) {
            return "RETREAT";
        }

        ActiveCommandSnapshot command = snapshot.command();

        if (command != null) {
            if (command.type() == PlayerCommandType.SURVIVE) {
                return "RETREAT";
            }

            if (command.type() == PlayerCommandType.CAPTURE) {
                return "CONTROL_CORE";
            }

            if (command.type() == PlayerCommandType.ATTACK
                && !living.isEmpty()) {
                return "FIGHT";
            }
        }

        if (snapshot.core().ownerUuid() == null) {
            return "CONTROL_CORE";
        }

        return living.isEmpty()
            ? "CONTROL_CORE"
            : "FIGHT";
    }

    private List<String> availableIntents(
        DecisionRequest request,
        List<EnemySnapshot> living
    ) {
        java.util.ArrayList<String> result =
            new java.util.ArrayList<>();

        if (!living.isEmpty()) {
            result.add("FIGHT");
        }

        if (request.validPlanIds().contains("CAPTURE_CORE")
            || request.validPlanIds().contains("DEFEND_CORE")) {
            result.add("CONTROL_CORE");
        }

        if (request.validPlanIds().contains("RETREAT")) {
            result.add("RETREAT");
        }

        return List.copyOf(result);
    }

    private String chooseTarget(
        List<EnemySnapshot> living
    ) {
        return living.stream()
            .min(
                java.util.Comparator
                    .comparingInt(EnemySnapshot::rank)
                    .thenComparingDouble(enemy ->
                        enemy.distance() == null
                            ? Double.MAX_VALUE
                            : enemy.distance()
                    )
            )
            .orElseThrow()
            .color()
            .name();
    }

    private List<String> availablePursuitStyles(
        DecisionRequest request
    ) {
        java.util.ArrayList<String> styles =
            new java.util.ArrayList<>();

        if (request.validPlanIds().stream()
            .anyMatch(id -> id.startsWith("ENGAGE_"))) {
            styles.add("ENGAGE");
        }

        if (request.validPlanIds().stream()
            .anyMatch(id -> id.startsWith("CHASE_"))) {
            styles.add("CHASE");
        }

        return List.copyOf(styles);
    }

    private String choosePursuit(
        RobotDecisionSnapshot snapshot,
        List<String> choices
    ) {
        ActiveCommandSnapshot command = snapshot.command();

        if (command != null
            && command.type() == PlayerCommandType.ATTACK
            && choices.contains("CHASE")) {
            return "CHASE";
        }

        if (choices.contains("ENGAGE")) {
            return "ENGAGE";
        }

        return choices.getFirst();
    }

    private ChoiceDecision deterministic(
        String selected,
        List<String> choices
    ) {
        Map<String, Double> probabilities =
            new LinkedHashMap<>();

        for (String choice : choices) {
            probabilities.put(
                choice,
                choice.equals(selected) ? 1.0D : 0.0D
            );
        }

        return new ChoiceDecision(
            selected,
            1.0D,
            probabilities
        );
    }
}
