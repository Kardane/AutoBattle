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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PlanValidityPolicy {
    private RobotConfig robotConfig;
    private Vec3 arenaCenter;
    private double arenaRadius;
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

        if (plan.type() == TacticalPlanType.ASSIST) {
            if (self.isPlanTemporarilyUnreachable(
                plan,
                currentTick
            )) {
                return PlanValidityResult.invalid(
                    PlanValidityStatus.TEMPORARILY_UNREACHABLE
                );
            }

            return validateAlly(
                match,
                self,
                plan.targetOwnerUuid()
            );
        }

        if (plan.type() != TacticalPlanType.ENGAGE
            && plan.type() != TacticalPlanType.CHASE) {
            return self.isPlanTemporarilyUnreachable(
                plan,
                currentTick
            )
                ? PlanValidityResult.invalid(
                    PlanValidityStatus
                        .TEMPORARILY_UNREACHABLE
                )
                : PlanValidityResult.validNonTarget();
        }

        return validateTarget(
            match,
            self,
            plan.targetOwnerUuid(),
            plan.type(),
            currentTick
        );
    }

    private PlanValidityResult validateAlly(
        MatchSession match,
        RobotController self,
        UUID allyOwnerUuid
    ) {
        if (allyOwnerUuid == null) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TARGET_MISSING
            );
        }

        RobotController ally = match.robots()
            .byOwner(allyOwnerUuid)
            .orElse(null);

        if (ally == null
            || ally == self
            || ally.team() != self.team()) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TARGET_INELIGIBLE
            );
        }

        PlayerSlot allySlot = match.player(
            allyOwnerUuid
        ).orElse(null);

        if (allySlot == null || allySlot.forfeited()) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TARGET_INELIGIBLE
            );
        }

        if (!self.alive() || !ally.alive()) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TARGET_DEAD
            );
        }

        RobotZombie selfEntity = self.entity().orElse(null);
        RobotZombie allyEntity = ally.entity().orElse(null);

        if (selfEntity == null
            || allyEntity == null
            || !selfEntity.isAlive()
            || !allyEntity.isAlive()
            || allyEntity.isRemoved()) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TARGET_DEAD
            );
        }

        if (!match.matchId().equals(
                selfEntity.matchId()
            )
            || !match.matchId().equals(
                allyEntity.matchId()
            )) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.WRONG_MATCH
            );
        }

        if (!insideArena(allyEntity.position())) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.OUTSIDE_ARENA
            );
        }

        TacticalPlan allyPlan = ally.currentPlan()
            .orElse(null);

        if (allyPlan != null
            && allyPlan.type() == TacticalPlanType.RETREAT) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.TARGET_INELIGIBLE
            );
        }

        if (createsAssistCycle(match, self, ally)) {
            return PlanValidityResult.invalid(
                PlanValidityStatus.ASSIST_CYCLE
            );
        }

        return PlanValidityResult.valid(
            allyEntity,
            selfEntity.distanceTo(allyEntity)
        );
    }

    private boolean createsAssistCycle(
        MatchSession match,
        RobotController self,
        RobotController ally
    ) {
        Set<UUID> visited = new HashSet<>();
        RobotController cursor = ally;

        while (cursor != null
            && visited.add(cursor.ownerUuid())) {
            TacticalPlan plan = cursor.currentPlan()
                .orElse(null);

            if (plan == null
                || plan.type() != TacticalPlanType.ASSIST) {
                return false;
            }

            UUID nextOwnerUuid = plan.targetOwnerUuid();

            if (nextOwnerUuid == null) {
                return false;
            }

            if (nextOwnerUuid.equals(self.ownerUuid())) {
                return true;
            }

            cursor = match.robots()
                .byOwner(nextOwnerUuid)
                .orElse(null);
        }

        return cursor != null;
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

    Vec3 arenaCenter() {
        return arenaCenter;
    }

    double arenaRadius() {
        return arenaRadius;
    }

    private void configureArena(ArenaConfig arena) {
        BlockPos core = arena.corePos();

        this.arenaCenter = new Vec3(
            core.getX() + 0.5D,
            core.getY(),
            core.getZ() + 0.5D
        );

        this.arenaRadius = arena.arenaRadius();

        this.arenaRadiusSqr =
            arenaRadius * arenaRadius;
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
