package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.command.PlayerCommandType;
import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.jev.DecisionTrigger;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.robot.RobotZombie;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

public final class ProvisionalPlanPolicy {
    private static final double IMMEDIATE_THREAT_RANGE = 4.0D;

    private AutoBattleConfig config;

    public ProvisionalPlanPolicy(AutoBattleConfig config) {
        reload(config);
    }

    public void reload(AutoBattleConfig config) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
    }

    public Optional<TacticalPlan> choose(
        MatchSession match,
        RobotController controller,
        List<TacticalPlan> candidates,
        long currentTick
    ) {
        return choose(
            match,
            controller,
            candidates,
            currentTick,
            null
        );
    }

    public Optional<TacticalPlan> choose(
        MatchSession match,
        RobotController controller,
        List<TacticalPlan> candidates,
        long currentTick,
        DecisionTrigger trigger
    ) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(candidates, "candidates");

        PlayerSlot slot = match.player(
            controller.ownerUuid()
        ).orElse(null);

        PlayerCommandType command = slot == null
            ? null
            : slot.runtime()
                .activeCommand()
                .filter(active -> active.active(currentTick))
                .map(active -> active.type())
                .orElse(null);

        RobotZombie self = controller.entity()
            .orElse(null);

        double hpRatio =
            self == null || self.getMaxHealth() <= 0.0F
                ? 0.0D
                : self.getHealth() / self.getMaxHealth();

        ToDoubleFunction<UUID> distanceToTarget =
            targetOwnerUuid -> match.robots()
                .byOwner(targetOwnerUuid)
                .flatMap(RobotController::entity)
                .filter(target ->
                    self != null
                        && target.matchId().equals(
                            self.matchId()
                        )
                )
                .map(target ->
                    (double) self.distanceTo(target)
                )
                .orElse(Double.POSITIVE_INFINITY);

        if (trigger == DecisionTrigger.RESPAWN) {
            Optional<TacticalPlan> hold =
                byId(candidates, "HOLD_POSITION");

            if (hold.isPresent()) {
                return hold;
            }
        }

        return chooseCandidate(
            candidates,
            command,
            hpRatio,
            config.ai().fallbackRetreatHpRatio(),
            distanceToTarget
        );
    }

    static Optional<TacticalPlan> chooseCandidate(
        List<TacticalPlan> candidates,
        PlayerCommandType command,
        double hpRatio,
        double dangerousHpRatio,
        ToDoubleFunction<UUID> distanceToTarget
    ) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(
            distanceToTarget,
            "distanceToTarget"
        );

        if (command != null) {
            Optional<TacticalPlan> commandPlan =
                switch (command) {
                    case ATTACK -> nearestCombat(
                        candidates,
                        distanceToTarget,
                        false
                    );
                    case CAPTURE -> objective(candidates);
                    case SURVIVE ->
                        retreatOrHold(candidates);
                };

            if (commandPlan.isPresent()) {
                return commandPlan;
            }
        }

        if (hpRatio <= dangerousHpRatio) {
            Optional<TacticalPlan> survival =
                retreatOrHold(candidates);

            if (survival.isPresent()) {
                return survival;
            }
        }

        Optional<TacticalPlan> immediateCombat =
            nearestCombat(
                candidates,
                distanceToTarget,
                true
            );

        if (immediateCombat.isPresent()) {
            return immediateCombat;
        }

        return objective(candidates);
    }

    private static Optional<TacticalPlan> retreatOrHold(
        List<TacticalPlan> candidates
    ) {
        Optional<TacticalPlan> retreat =
            byId(candidates, "RETREAT");

        return retreat.isPresent()
            ? retreat
            : byId(candidates, "HOLD_POSITION");
    }

    private static Optional<TacticalPlan> nearestCombat(
        List<TacticalPlan> candidates,
        ToDoubleFunction<UUID> distanceToTarget,
        boolean immediateOnly
    ) {
        Optional<TacticalPlan> engage =
            candidates.stream()
                .filter(plan ->
                    plan.type() == TacticalPlanType.ENGAGE
                )
                .filter(plan ->
                    !immediateOnly
                        || distanceToTarget.applyAsDouble(
                            plan.targetOwnerUuid()
                        ) <= IMMEDIATE_THREAT_RANGE
                )
                .min(
                    Comparator.comparingDouble(
                        plan ->
                            distanceToTarget.applyAsDouble(
                                plan.targetOwnerUuid()
                            )
                    )
                );

        if (engage.isPresent() || immediateOnly) {
            return engage;
        }

        return candidates.stream()
            .filter(plan ->
                plan.type() == TacticalPlanType.CHASE
            )
            .min(
                Comparator.comparingDouble(
                    plan ->
                        distanceToTarget.applyAsDouble(
                            plan.targetOwnerUuid()
                        )
                )
            );
    }

    private static Optional<TacticalPlan> objective(
        List<TacticalPlan> candidates
    ) {
        Optional<TacticalPlan> defend =
            byId(candidates, "DEFEND_CORE");

        return defend.isPresent()
            ? defend
            : byId(candidates, "CAPTURE_CORE");
    }

    private static Optional<TacticalPlan> byId(
        List<TacticalPlan> candidates,
        String externalId
    ) {
        return candidates.stream()
            .filter(plan ->
                externalId.equals(plan.externalId())
            )
            .findFirst();
    }

}
