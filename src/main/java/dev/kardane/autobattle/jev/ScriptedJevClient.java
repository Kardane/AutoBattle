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
        String selected = choose(request);

        Map<String, Double> probabilities =
            new LinkedHashMap<>();

        for (String candidate : request.validPlanIds()) {
            probabilities.put(
                candidate,
                candidate.equals(selected) ? 1.0D : 0.0D
            );
        }

        return CompletableFuture.completedFuture(
            new DecisionResponse(
                selected,
                1.0D,
                probabilities,
                0L
            )
        );
    }

    private String choose(DecisionRequest request) {
        RobotDecisionSnapshot snapshot = request.snapshot();
        List<String> valid = request.validPlanIds();

        if (valid.isEmpty()) {
            throw new IllegalStateException(
                "No valid tactical plan candidates."
            );
        }

        double hpRatio = snapshot.self().maxHp() <= 0.0F
            ? 0.0D
            : snapshot.self().hp() / snapshot.self().maxHp();

        if (hpRatio <= 0.25D && valid.contains("RETREAT")) {
            return "RETREAT";
        }

        ActiveCommandSnapshot command = snapshot.command();

        if (command != null) {
            if (command.type() == PlayerCommandType.SURVIVE
                && valid.contains("RETREAT")) {
                return "RETREAT";
            }

            if (command.type() == PlayerCommandType.CAPTURE) {
                String objective = firstWithPrefix(
                    valid,
                    "CAPTURE_"
                );

                if (objective == null) {
                    objective = firstWithPrefix(
                        valid,
                        "DEFEND_"
                    );
                }

                if (objective != null) {
                    return objective;
                }
            }

            if (command.type() == PlayerCommandType.ATTACK) {
                String chase = firstWithPrefix(
                    valid,
                    "CHASE_"
                );

                if (chase != null) {
                    return chase;
                }
            }
        }

        String capture = firstWithPrefix(
            valid,
            "CAPTURE_"
        );

        if (snapshot.core().ownerUuid() == null
            && capture != null) {
            return capture;
        }

        String engage = firstWithPrefix(
            valid,
            "ENGAGE_"
        );

        if (engage != null) {
            return engage;
        }

        if (valid.contains("REPOSITION")) {
            return "REPOSITION";
        }

        return valid.getFirst();
    }

    private String firstWithPrefix(
        List<String> candidates,
        String prefix
    ) {
        for (String candidate : candidates) {
            if (candidate.startsWith(prefix)) {
                return candidate;
            }
        }

        return null;
    }
}
