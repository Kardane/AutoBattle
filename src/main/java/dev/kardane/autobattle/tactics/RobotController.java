package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.AutoBattleConstants;
import dev.kardane.autobattle.robot.RobotColor;
import dev.kardane.autobattle.robot.RobotRegistry;
import dev.kardane.autobattle.robot.RobotRuntimeState;
import dev.kardane.autobattle.robot.RobotZombie;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RobotController {
    private static final double ENGAGE_SPEED = 1.00D;
    private static final double CHASE_SPEED = 1.20D;
    private static final double CAPTURE_SPEED = 1.05D;
    private static final double DEFEND_SPEED = 1.00D;
    private static final double REPOSITION_SPEED = 1.10D;
    private static final double RETREAT_SPEED = 1.20D;

    private static final double POSITION_REACHED_DISTANCE_SQR = 2.25D;
    private static final double DEFEND_RADIUS_SQR = 9.0D;
    private static final double RETREAT_DISTANCE = 8.0D;

    private final UUID ownerUuid;
    private final RobotColor color;
    private final RobotRegistry registry;
    private final RobotRuntimeState runtime =
        new RobotRuntimeState();

    private RobotZombie entity;
    private TacticalPlan currentPlan;
    private long planStartedTick = -1L;
    private long lastDecisionTick = -1L;
    private boolean decisionPending;
    private boolean redecisionRequested;
    private long decisionGeneration;

    public RobotController(
        UUID ownerUuid,
        RobotColor color,
        RobotRegistry registry
    ) {
        this.ownerUuid = Objects.requireNonNull(
            ownerUuid,
            "ownerUuid"
        );
        this.color = Objects.requireNonNull(color, "color");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public RobotColor color() {
        return color;
    }

    public Optional<RobotZombie> entity() {
        return Optional.ofNullable(entity);
    }

    public Optional<UUID> entityUuid() {
        return entity().map(RobotZombie::getUUID);
    }

    public boolean alive() {
        return runtime.alive()
            && entity != null
            && !entity.isRemoved()
            && entity.isAlive();
    }

    public RobotRuntimeState runtime() {
        return runtime;
    }

    public Optional<TacticalPlan> currentPlan() {
        return Optional.ofNullable(currentPlan);
    }

    public long planStartedTick() {
        return planStartedTick;
    }

    public long lastDecisionTick() {
        return lastDecisionTick;
    }

    public boolean hasPendingDecision() {
        return decisionPending;
    }

    public long decisionGeneration() {
        return decisionGeneration;
    }

    public void attachEntity(
        RobotZombie entity,
        long currentTick
    ) {
        Objects.requireNonNull(entity, "entity");

        if (!entity.ownerUuid().equals(ownerUuid)) {
            throw new IllegalArgumentException(
                "Robot entity owner does not match controller owner."
            );
        }

        if (entity.robotColor() != color) {
            throw new IllegalArgumentException(
                "Robot entity color does not match controller color."
            );
        }

        this.entity = entity;
        runtime.markSpawned(currentTick);
        registry.reindexEntity(this);
    }

    public void detachEntity() {
        clearPlan();
        entity = null;
        registry.reindexEntity(this);
    }

    public boolean applyPlan(
        TacticalPlan plan,
        long currentTick
    ) {
        Objects.requireNonNull(plan, "plan");

        if (currentPlan != null && currentPlan.isLocked(currentTick)) {
            return false;
        }

        currentPlan = plan;
        planStartedTick = currentTick;
        lastDecisionTick = currentTick;
        redecisionRequested = false;
        return true;
    }

    public void clearPlan() {
        currentPlan = null;
        planStartedTick = -1L;

        if (entity != null && !entity.isRemoved()) {
            entity.setTarget(null);
            entity.getNavigation().stop();
        }
    }

    public void markDecisionRequested(long generation) {
        decisionPending = true;
        decisionGeneration = generation;
    }

    public void markDecisionCompleted(
        long generation,
        long currentTick
    ) {
        if (generation != decisionGeneration) {
            return;
        }

        decisionPending = false;
        lastDecisionTick = currentTick;
        redecisionRequested = false;
    }

    public void requestRedecision() {
        redecisionRequested = true;
    }

    public boolean consumeRedecisionFlag() {
        boolean value = redecisionRequested;
        redecisionRequested = false;
        return value;
    }

    public long nextDecisionGeneration() {
        return ++decisionGeneration;
    }

    public boolean shouldRequestDecision(
        long currentTick,
        int intervalTicks,
        int lockTicks
    ) {
        if (!alive() || decisionPending) {
            return false;
        }

        if (currentPlan == null) {
            return true;
        }

        long lockEndTick = Math.max(
            planStartedTick + lockTicks,
            currentPlan.lockUntilTick()
        );

        if (currentTick < lockEndTick) {
            return false;
        }

        if (redecisionRequested) {
            return true;
        }

        return lastDecisionTick < 0L
            || currentTick - lastDecisionTick >= intervalTicks;
    }

    public void tick(long currentTick) {
        if (!alive() || runtime.frozen()) {
            return;
        }

        if (currentPlan == null) {
            entity.setTarget(null);
            return;
        }

        switch (currentPlan.type()) {
            case ENGAGE -> engage(
                AutoBattleConstants.ENGAGE_LEASH_DISTANCE
            );
            case CHASE -> chase(
                AutoBattleConstants.CHASE_LEASH_DISTANCE
            );
            case CAPTURE -> moveToPosition(
                currentPlan.destination(),
                CAPTURE_SPEED,
                POSITION_REACHED_DISTANCE_SQR
            );
            case DEFEND -> defend(currentPlan.destination());
            case RETREAT -> retreat();
            case REPOSITION -> moveToPosition(
                currentPlan.destination(),
                REPOSITION_SPEED,
                POSITION_REACHED_DISTANCE_SQR
            );
        }
    }

    private void engage(double leashDistance) {
        followCombatTarget(leashDistance, ENGAGE_SPEED);
    }

    private void chase(double leashDistance) {
        followCombatTarget(leashDistance, CHASE_SPEED);
    }

    private void followCombatTarget(
        double leashDistance,
        double speed
    ) {
        resolvePlanTarget().ifPresentOrElse(
            target -> {
                double leashDistanceSqr =
                    leashDistance * leashDistance;

                if (entity.distanceToSqr(target) > leashDistanceSqr) {
                    invalidateCurrentTarget();
                    return;
                }

                entity.setTarget(target);
                entity.getNavigation().moveTo(target, speed);
            },
            this::invalidateCurrentTarget
        );
    }

    private void defend(Vec3 destination) {
        entity.setTarget(null);

        if (entity.position().distanceToSqr(destination) > DEFEND_RADIUS_SQR) {
            entity.getNavigation().moveTo(
                destination.x,
                destination.y,
                destination.z,
                DEFEND_SPEED
            );
        } else {
            entity.getNavigation().stop();
        }
    }

    private void retreat() {
        entity.setTarget(null);

        resolvePlanTarget().ifPresentOrElse(
            threat -> {
                Vec3 away = entity.position()
                    .subtract(threat.position());

                Vec3 horizontal = new Vec3(
                    away.x,
                    0.0D,
                    away.z
                );

                if (horizontal.lengthSqr() < 1.0E-4D) {
                    horizontal = new Vec3(1.0D, 0.0D, 0.0D);
                } else {
                    horizontal = horizontal.normalize();
                }

                Vec3 destination = entity.position()
                    .add(horizontal.scale(RETREAT_DISTANCE));

                entity.getNavigation().moveTo(
                    destination.x,
                    destination.y,
                    destination.z,
                    RETREAT_SPEED
                );
            },
            this::invalidateCurrentTarget
        );
    }

    private void moveToPosition(
        Vec3 destination,
        double speed,
        double reachedDistanceSqr
    ) {
        entity.setTarget(null);

        if (entity.position().distanceToSqr(destination)
            <= reachedDistanceSqr) {
            entity.getNavigation().stop();
            return;
        }

        entity.getNavigation().moveTo(
            destination.x,
            destination.y,
            destination.z,
            speed
        );
    }

    private Optional<RobotZombie> resolvePlanTarget() {
        if (currentPlan == null
            || currentPlan.targetOwnerUuid() == null) {
            return Optional.empty();
        }

        return registry
            .byOwner(currentPlan.targetOwnerUuid())
            .filter(target -> target != this)
            .filter(RobotController::alive)
            .flatMap(RobotController::entity)
            .filter(target ->
                entity != null
                    && target.matchId().equals(entity.matchId())
            );
    }

    private void invalidateCurrentTarget() {
        if (entity != null && !entity.isRemoved()) {
            entity.setTarget(null);
            entity.getNavigation().stop();
        }

        requestRedecision();
    }
}
