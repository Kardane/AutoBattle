package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ValidPlanFactory {
    private AutoBattleConfig config;
    private final PlanValidityPolicy validityPolicy;

    public ValidPlanFactory(AutoBattleConfig config) {
        this(
            config,
            new PlanValidityPolicy(config)
        );
    }

    public ValidPlanFactory(
        AutoBattleConfig config,
        PlanValidityPolicy validityPolicy
    ) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
        this.validityPolicy = Objects.requireNonNull(
            validityPolicy,
            "validityPolicy"
        );
    }

    public void reload(AutoBattleConfig config) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
        validityPolicy.reload(config);
    }

    public List<TacticalPlan> create(
        MatchSession match,
        RobotController self,
        long currentTick
    ) {
        if (match.phase() != MatchPhase.ROUND_ACTIVE
            || !self.alive()) {
            return List.of();
        }

        List<TacticalPlan> plans = new ArrayList<>();
        long lockTicks = config.decisionLockTicks();

        for (RobotController enemy : match.robots().all()) {
            if (enemy == self) {
                continue;
            }

            String suffix = enemy.targetId();

            TacticalPlan engage = TacticalPlan.engage(
                enemy.ownerUuid(),
                "ENGAGE_" + suffix,
                currentTick,
                lockTicks
            );

            if (validityPolicy.validate(
                match,
                self,
                engage,
                currentTick
            ).valid()) {
                plans.add(engage);
            }

            TacticalPlan chase = TacticalPlan.chase(
                enemy.ownerUuid(),
                "CHASE_" + suffix,
                currentTick,
                lockTicks
            );

            if (validityPolicy.validate(
                match,
                self,
                chase,
                currentTick
            ).valid()) {
                plans.add(chase);
            }
        }

        var coreOwner = match.core()
            .state()
            .ownerTeam()
            .orElse(null);

        TacticalPlan objective =
            self.team() == coreOwner
                ? TacticalPlan.defend(
                    coreCenter(match),
                    currentTick,
                    lockTicks
                )
                : TacticalPlan.capture(
                    coreCenter(match),
                    currentTick,
                    lockTicks
                );

        if (validityPolicy.validate(
            match,
            self,
            objective,
            currentTick
        ).valid()) {
            plans.add(objective);
        }

        TacticalPlan retreat =
            TacticalPlan.retreat(
                currentTick,
                lockTicks
            );

        if (validityPolicy.validate(
            match,
            self,
            retreat,
            currentTick
        ).valid()) {
            plans.add(retreat);
        }

        return List.copyOf(plans);
    }

    private Vec3 coreCenter(MatchSession match) {
        BlockPos pos = match.core().position();

        return new Vec3(
            pos.getX() + 0.5D,
            pos.getY() + 0.5D,
            pos.getZ() + 0.5D
        );
    }
}
