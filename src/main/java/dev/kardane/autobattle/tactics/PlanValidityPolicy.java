package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.config.RobotConfig;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.robot.RobotZombie;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

public final class PlanValidityPolicy {
    private RobotConfig robotConfig;
    private Vec3 arenaCenter;
    private double arenaRadiusSqr;

    public PlanValidityPolicy(AutoBattleConfig config) {
        reload(config);
    }

    public void reload(AutoBattleConfig config) {
        Objects.requireNonNull(config, "config");

        this.robotConfig = config.robot();
        configureArena(config.arena());
    }

    public PlanValidityResult validate(
        MatchSession match,
        RobotController self,
        TacticalPlan plan,
        long currentTick
    ) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(plan, "plan");

        if (plan.type() != TacticalPlanType.ENGAGE
            && plan.type() != TacticalPlanType.CHASE) {
            return PlanValidityResult.valid();
        }

        return validateTarget(
            match,
            self,
            plan.targetOwnerUuid(),
            plan.type(),
            currentTick
        );
    }

    public PlanValidityResult validateTarget(
        MatchSession match,
        RobotController self,
        UUID targetOwnerUuid,
        TacticalPlanType type,
        long currentTick
    ) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(self, "self");
        Objects.requireNonNull(type, "type");

        if (type != TacticalPlanType.ENGAGE
            && type != TacticalPlanType.CHASE) {
            throw new IllegalArgumentException(
                "Target validity only applies to ENGAGE/CHASE"
            );
        }

        if (targetOwnerUuid == null) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TARGET_MISSING
            );
        }

        RobotController targetController = match.robots()
            .byOwner(targetOwnerUuid)
            .orElse(null);

        if (targetController == null) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TARGET_MISSING
            );
        }

        if (targetController == self
            || !self.team().isEnemy(
                targetController.team()
            )) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TARGET_INELIGIBLE
            );
        }

        PlayerSlot targetSlot = match.player(
            targetOwnerUuid
        ).orElse(null);

        if (targetSlot == null || targetSlot.forfeited()) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TARGET_INELIGIBLE
            );
        }

        if (!self.alive()
            || !targetController.alive()) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TARGET_DEAD
            );
        }

        RobotZombie selfEntity = self.entity().orElse(null);
        RobotZombie targetEntity =
            targetController.entity().orElse(null);

        if (selfEntity == null
            || targetEntity == null
            || !selfEntity.isAlive()
            || !targetEntity.isAlive()
            || targetEntity.isRemoved()) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TARGET_DEAD
            );
        }

        if (!match.matchId().equals(
                selfEntity.matchId()
            )
            || !match.matchId().equals(
                targetEntity.matchId()
            )) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.WRONG_MATCH
            );
        }

        if (!insideArena(targetEntity.position())) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.OUTSIDE_ARENA
            );
        }

        if (self.isTargetTemporarilyUnreachable(
            targetOwnerUuid,
            targetEntity.position(),
            currentTick
        )) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TEMPORARILY_UNREACHABLE
            );
        }

        double distance =
            selfEntity.distanceTo(targetEntity);
        double maxDistance = switch (type) {
            case ENGAGE ->
                robotConfig.engageLeashDistance();
            case CHASE ->
                robotConfig.chaseLeashDistance();
            default -> throw new IllegalStateException(
                "Unexpected targeted plan type: " + type
            );
        };

        if (distance > maxDistance) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.OUT_OF_RANGE
            );
        }

        return PlanValidityResult.valid(
            targetEntity,
            distance
        );
    }

    public boolean insideArena(Vec3 position) {
        return horizontalDistanceSqr(
            arenaCenter,
            position
        ) <= arenaRadiusSqr;
    }

    private void configureArena(ArenaConfig arena) {
        BlockPos core = arena.corePos();

        this.arenaCenter = new Vec3(
            core.getX() + 0.5D,
            core.getY(),
            core.getZ() + 0.5D
        );

        double radius = calculateArenaRadius(
            arena,
            arenaCenter,
            robotConfig.positionReachedDistance()
        );

        this.arenaRadiusSqr = radius * radius;
    }

    private static double calculateArenaRadius(
        ArenaConfig arena,
        Vec3 center,
        double margin
    ) {
        double halfTeamSpan =
            3.5D * arena.teamSpawns().memberSpacing();

        double spawnRadius = Math.hypot(
            arena.teamSpawns().distanceFromCore(),
            halfTeamSpan
        );

        double maxRadius = Math.max(
            arena.coreRadius(),
            spawnRadius
        );

        return maxRadius + Math.max(1.0D, margin);
    }

    private static double horizontalDistanceSqr(
        Vec3 first,
        Vec3 second
    ) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return dx * dx + dz * dz;
    }
}
