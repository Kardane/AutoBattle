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
            .filter(enemy ->
                enemy.team() != snapshot.self().team()
            )
            .filter(enemy -> {
                String targetId = enemy.targetId();

                return request.validPlanIds().contains(
                    "ENGAGE_" + targetId
                ) || request.validPlanIds().contains(
                    "CHASE_" + targetId
                );
            })
            .toList();
        List<AllySnapshot> supportCandidates =
            snapshot.allies()
                .stream()
                .filter(AllySnapshot::alive)
                .filter(ally -> request.validPlanIds()
                    .contains("ASSIST_" + ally.targetId()))
                .toList();

        String intentChoice = chooseIntent(
            request,
            living,
            supportCandidates
        );

        ChoiceDecision intent = deterministic(
            intentChoice,
            availableIntents(
                request,
                living,
                supportCandidates
            )
        );

        ChoiceDecision target = null;
        ChoiceDecision pursuit = null;

        if (!living.isEmpty()) {
            String targetChoice = chooseTarget(living);
            target = deterministic(
                targetChoice,
                living.stream()
                    .map(EnemySnapshot::targetId)
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

        ChoiceDecision allyTarget = null;

        if (!supportCandidates.isEmpty()) {
            String targetId = chooseSupportTarget(
                supportCandidates
            );
            allyTarget = deterministic(
                targetId,
                supportCandidates.stream()
                    .map(AllySnapshot::targetId)
                    .toList()
            );
        }

        return CompletableFuture.completedFuture(
            new DecisionResponse(
                intent,
                target,
                pursuit,
                allyTarget,
                0L
            )
        );
    }

    private String chooseIntent(
        DecisionRequest request,
        List<EnemySnapshot> living,
        List<AllySnapshot> supportCandidates
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

        if (!supportCandidates.isEmpty()
            && supportCandidates.stream().anyMatch(
                ally -> ally.underAttack()
                    || ally.hpRatio() <= 0.4D
            )) {
            return "SUPPORT";
        }

        if (request.validPlanIds().contains("HOLD_POSITION")
            && snapshot.teamContext().aliveEnemies()
                > snapshot.teamContext().aliveAllies() + 1) {
            return "HOLD";
        }

        if (snapshot.core().contested()
            && !living.isEmpty()) {
            return "FIGHT";
        }

        if (snapshot.core().ownerTeam() == null
            || snapshot.core().ownerTeam()
                != snapshot.self().team()) {
            return "CONTROL_CORE";
        }

        return living.isEmpty()
            ? "CONTROL_CORE"
            : "FIGHT";
    }

    private List<String> availableIntents(
        DecisionRequest request,
        List<EnemySnapshot> living,
        List<AllySnapshot> supportCandidates
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

        if (!supportCandidates.isEmpty()) {
            result.add("SUPPORT");
        }

        if (request.validPlanIds().contains("HOLD_POSITION")) {
            result.add("HOLD");
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
            .targetId();
    }

    private String chooseSupportTarget(
        List<AllySnapshot> allies
    ) {
        return allies.stream()
            .min(
                java.util.Comparator
                    .comparing(AllySnapshot::underAttack)
                    .reversed()
                    .thenComparingDouble(
                        AllySnapshot::hpRatio
                    )
                    .thenComparingDouble(ally ->
                        ally.distance() == null
                            ? Double.MAX_VALUE
                            : ally.distance()
                    )
            )
            .orElseThrow()
            .targetId();
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
