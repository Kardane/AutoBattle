package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.AutoBattleConstants;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ValidPlanFactory {
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
        long lockTicks = AutoBattleConstants.DECISION_LOCK_TICKS;

        for (RobotController enemy : match.robots().alive()) {
            if (enemy == self) {
                continue;
            }

            PlayerSlot enemySlot = match.player(
                enemy.ownerUuid()
            ).orElse(null);

            if (enemySlot == null || enemySlot.forfeited()) {
                continue;
            }

            String suffix = enemy.color().name();

            plans.add(
                TacticalPlan.engage(
                    enemy.ownerUuid(),
                    "ENGAGE_" + suffix,
                    currentTick,
                    lockTicks
                )
            );

            plans.add(
                TacticalPlan.chase(
                    enemy.ownerUuid(),
                    "CHASE_" + suffix,
                    currentTick,
                    lockTicks
                )
            );
        }

        UUID coreOwner = match.core()
            .state()
            .ownerUuid()
            .orElse(null);

        if (self.ownerUuid().equals(coreOwner)) {
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

        Vec3 reposition = repositionDestination(
            match,
            self
        );

        if (reposition != null) {
            plans.add(
                TacticalPlan.reposition(
                    reposition,
                    currentTick,
                    lockTicks
                )
            );
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

    private Vec3 repositionDestination(
        MatchSession match,
        RobotController self
    ) {
        List<BlockPos> nodes = match.robots()
            .all()
            .isEmpty()
            ? List.of()
            : null;

        PlayerSlot slot = match.player(
            self.ownerUuid()
        ).orElse(null);

        if (slot == null) {
            return null;
        }

        // Arena nodes are not owned by MatchSession yet. Until the planner
        // receives ArenaConfig directly, use a simple offset around CORE.
        Vec3 core = coreCenter(match);
        double offset = 7.0D;

        return switch (slot.slotIndex() % 4) {
            case 0 -> core.add(offset, 0.0D, offset);
            case 1 -> core.add(-offset, 0.0D, offset);
            case 2 -> core.add(-offset, 0.0D, -offset);
            default -> core.add(offset, 0.0D, -offset);
        };
    }
}
