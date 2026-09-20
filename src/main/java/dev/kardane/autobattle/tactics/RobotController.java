package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.config.RobotConfig;
import dev.kardane.autobattle.robot.RobotColor;
import dev.kardane.autobattle.robot.RobotRegistry;
import dev.kardane.autobattle.robot.RobotRuntimeState;
import dev.kardane.autobattle.robot.RobotZombie;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RobotController {
    private final UUID ownerUuid;
    private final RobotColor color;
    private final RobotRegistry registry;
    private final RobotConfig config;
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
        RobotRegistry registry,
        RobotConfig config
    ) {
        this.ownerUuid = Objects.requireNonNull(
            ownerUuid,
            "ownerUuid"
        );
        this.color = Objects.requireNonNull(color, "color");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
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
        decisionPending = false;
        decisionGeneration++;
        redecisionRequested = true;
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
        redecisionRequested = false;
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
                config.engageLeashDistance()
            );
            case CHASE -> chase(
                config.chaseLeashDistance()
            );
            case CAPTURE -> moveToPosition(
                currentPlan.destination(),
                config.captureSpeed(),
                square(config.positionReachedDistance()),
                false
            );
            case DEFEND -> defend(currentPlan.destination());
            case RETREAT -> retreat();
            case REPOSITION -> moveToPosition(
                currentPlan.destination(),
                config.repositionSpeed(),
                square(config.positionReachedDistance()),
                true
            );
        }
    }

    private void engage(double leashDistance) {
        followCombatTarget(leashDistance, config.engageSpeed());
    }

    private void chase(double leashDistance) {
        followCombatTarget(leashDistance, config.chaseSpeed());
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

        if (entity.position().distanceToSqr(destination) > square(config.defendRadius())) {
            entity.getNavigation().moveTo(
                destination.x,
                destination.y,
                destination.z,
                config.defendSpeed()
            );
        } else {
            entity.getNavigation().stop();
        }
    }

    private void retreat() {
        entity.setTarget(null);

        resolveNearestEnemy().ifPresentOrElse(
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
                    .add(horizontal.scale(config.retreatDistance()));

                entity.getNavigation().moveTo(
                    destination.x,
                    destination.y,
                    destination.z,
                    config.retreatSpeed()
                );
            },
            this::invalidateCurrentTarget
        );
    }

    private void moveToPosition(
        Vec3 destination,
        double speed,
        double reachedDistanceSqr,
        boolean completeOnArrival
    ) {
        entity.setTarget(null);

        if (entity.position().distanceToSqr(destination)
            <= reachedDistanceSqr) {
            entity.getNavigation().stop();

            if (completeOnArrival) {
                clearPlan();
                requestRedecision();
            }

            return;
        }

        entity.getNavigation().moveTo(
            destination.x,
            destination.y,
            destination.z,
            speed
        );
    }

    private Optional<RobotZombie> resolveNearestEnemy() {
        if (entity == null) {
            return Optional.empty();
        }

        return registry.alive().stream()
            .filter(controller -> controller != this)
            .flatMap(controller -> controller.entity().stream())
            .filter(target ->
                target.matchId().equals(entity.matchId())
            )
            .min(
                java.util.Comparator.comparingDouble(
                    target -> entity.distanceToSqr(target)
                )
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

    private double square(double value) {
        return value * value;
    }

    private void invalidateCurrentTarget() {
        if (entity != null && !entity.isRemoved()) {
            entity.setTarget(null);
            entity.getNavigation().stop();
        }

        requestRedecision();
    }
}
