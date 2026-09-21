package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ValidPlanFactory {
    private AutoBattleConfig config;

    public ValidPlanFactory(AutoBattleConfig config) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
    }

    public void reload(AutoBattleConfig config) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
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
        var selfEntity = self.entity().orElseThrow();

        for (RobotController enemy : match.robots().alive()) {
            if (enemy == self
                || enemy.team() == self.team()) {
                continue;
            }

            PlayerSlot enemySlot = match.player(
                enemy.ownerUuid()
            ).orElse(null);

            if (enemySlot == null || enemySlot.forfeited()) {
                continue;
            }

            var enemyEntity = enemy.entity().orElse(null);

            if (enemyEntity == null) {
                continue;
            }

            double distance =
                selfEntity.distanceTo(enemyEntity);
            String suffix = enemy.targetId();

            if (distance <= config.robot()
                .engageLeashDistance()) {
                plans.add(
                    TacticalPlan.engage(
                        enemy.ownerUuid(),
                        "ENGAGE_" + suffix,
                        currentTick,
                        lockTicks
                    )
                );
            }

            if (distance <= config.robot()
                .chaseLeashDistance()) {
                plans.add(
                    TacticalPlan.chase(
                        enemy.ownerUuid(),
                        "CHASE_" + suffix,
                        currentTick,
                        lockTicks
                    )
                );
            }
        }

        var coreOwner = match.core()
            .state()
            .ownerTeam()
            .orElse(null);

        if (self.team() == coreOwner) {
            plans.add(
                TacticalPlan.defend(
                    coreCenter(match),
                    currentTick,
                    lockTicks
                )
            );
        } else {
            plans.add(
                TacticalPlan.capture(
                    coreCenter(match),
                    currentTick,
                    lockTicks
                )
            );
        }

        plans.add(
            TacticalPlan.retreat(
                currentTick,
                lockTicks
            )
        );

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
