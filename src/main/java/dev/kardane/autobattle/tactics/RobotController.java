package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.config.RobotConfig;
import dev.kardane.autobattle.robot.RobotColor;
import dev.kardane.autobattle.robot.RobotRegistry;
import dev.kardane.autobattle.robot.RobotRuntimeState;
import dev.kardane.autobattle.robot.RobotZombie;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RobotController {
    private static final double INTENT_PARTICLE_SPACING = 0.2D;
    private static final float INTENT_PARTICLE_SCALE = 0.9F;

    private final UUID ownerUuid;
    private final RobotColor color;
    private final RobotRegistry registry;
    private final RobotConfig config;
    private final RobotRuntimeState runtime =
        new RobotRuntimeState();

    private final Vec3 arenaCenter;
    private final double arenaRadius;
    private final double arenaRadiusSqr;

    private RobotZombie entity;
    private TacticalPlan currentPlan;
    private long planStartedTick = -1L;
    private long lastDecisionTick = -1L;
    private boolean decisionPending;
    private boolean redecisionRequested;
    private long decisionGeneration;
    private UUID localCombatTargetUuid;

    public RobotController(
        UUID ownerUuid,
        RobotColor color,
        RobotRegistry registry,
        RobotConfig config,
        ArenaConfig arena
    ) {
        this.ownerUuid = Objects.requireNonNull(
            ownerUuid,
            "ownerUuid"
        );
        this.color = Objects.requireNonNull(color, "color");
        this.registry = Objects.requireNonNull(
            registry,
            "registry"
        );
        this.config = Objects.requireNonNull(
            config,
            "config"
        );

        Objects.requireNonNull(arena, "arena");

        BlockPos core = arena.corePos();

        this.arenaCenter = new Vec3(
            core.getX() + 0.5D,
            core.getY(),
            core.getZ() + 0.5D
        );
        this.arenaRadius = calculateArenaRadius(
            arena,
            arenaCenter,
            config.positionReachedDistance()
        );
        this.arenaRadiusSqr =
            arenaRadius * arenaRadius;
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
        this.localCombatTargetUuid = null;
        runtime.markSpawned(currentTick);
        registry.reindexEntity(this);
    }

    public void detachEntity() {
        clearPlan();
        entity = null;
        localCombatTargetUuid = null;
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

        if (currentPlan != null
            && currentPlan.isLocked(currentTick)) {
            return false;
        }

        currentPlan = plan;
        planStartedTick = currentTick;
        lastDecisionTick = currentTick;
        localCombatTargetUuid = plan.targetOwnerUuid();

        renderPlanIntent(plan);
        return true;
    }

    public void clearPlan() {
        currentPlan = null;
        planStartedTick = -1L;
        localCombatTargetUuid = null;

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

        if ((currentPlan == null
            || currentPlan.type() != TacticalPlanType.RETREAT)
            && attackNearbyEnemy()) {
            return;
        }

        if (currentPlan == null) {
            entity.setTarget(null);
            entity.getNavigation().stop();
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
            case DEFEND ->
                defend(currentPlan.destination());
            case RETREAT -> retreat();
            case REPOSITION -> moveToPosition(
                currentPlan.destination(),
                config.repositionSpeed(),
                square(config.positionReachedDistance()),
                true
            );
        }
    }

    private boolean attackNearbyEnemy() {
        RobotZombie target = resolveNearestEnemy()
            .filter(candidate ->
                entity.distanceToSqr(candidate)
                    <= square(config.engageLeashDistance())
            )
            .orElse(null);

        if (target == null) {
            localCombatTargetUuid = null;
            return false;
        }

        if (!target.ownerUuid().equals(
            localCombatTargetUuid
        )) {
            localCombatTargetUuid = target.ownerUuid();
            renderIntentLine(
                entityAimPoint(target)
            );
        }

        entity.setTarget(target);
        entity.getNavigation().moveTo(
            target,
            config.engageSpeed()
        );

        return true;
    }

    private void engage(double leashDistance) {
        followCombatTarget(
            leashDistance,
            config.engageSpeed()
        );
    }

    private void chase(double leashDistance) {
        followCombatTarget(
            leashDistance,
            config.chaseSpeed()
        );
    }

    private void followCombatTarget(
        double leashDistance,
        double speed
    ) {
        resolvePlanTarget().ifPresentOrElse(
            target -> {
                if (!insideArena(target.position())
                    || entity.distanceToSqr(target)
                        > square(leashDistance)) {
                    invalidateCurrentTarget();
                    return;
                }

                entity.setTarget(target);
                entity.getNavigation().moveTo(
                    target,
                    speed
                );
            },
            this::invalidateCurrentTarget
        );
    }

    private void defend(Vec3 destination) {
        entity.setTarget(null);

        if (entity.position().distanceToSqr(destination)
            > square(config.defendRadius())) {
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

        retreatDestination().ifPresentOrElse(
            destination ->
                entity.getNavigation().moveTo(
                    destination.x,
                    destination.y,
                    destination.z,
                    config.retreatSpeed()
                ),
            this::invalidateCurrentTarget
        );
    }

    private Optional<Vec3> retreatDestination() {
        return resolveNearestEnemy().map(threat -> {
            Vec3 away = entity.position()
                .subtract(threat.position());

            Vec3 horizontal = new Vec3(
                away.x,
                0.0D,
                away.z
            );

            if (horizontal.lengthSqr() < 1.0E-4D) {
                horizontal = new Vec3(
                    1.0D,
                    0.0D,
                    0.0D
                );
            } else {
                horizontal = horizontal.normalize();
            }

            Vec3 destination = entity.position()
                .add(
                    horizontal.scale(
                        config.retreatDistance()
                    )
                );

            return clampToArena(destination);
        });
    }

    private void moveToPosition(
        Vec3 destination,
        double speed,
        double reachedDistanceSqr,
        boolean completeOnArrival
    ) {
        entity.setTarget(null);

        Vec3 boundedDestination =
            clampToArena(destination);

        if (entity.position().distanceToSqr(
            boundedDestination
        ) <= reachedDistanceSqr) {
            entity.getNavigation().stop();

            if (completeOnArrival) {
                clearPlan();
                requestRedecision();
            }

            return;
        }

        entity.getNavigation().moveTo(
            boundedDestination.x,
            boundedDestination.y,
            boundedDestination.z,
            speed
        );
    }

    private Optional<RobotZombie> resolveNearestEnemy() {
        if (entity == null) {
            return Optional.empty();
        }

        return registry.alive().stream()
            .filter(controller -> controller != this)
            .flatMap(
                controller ->
                    controller.entity().stream()
            )
            .filter(target ->
                target.matchId().equals(entity.matchId())
            )
            .filter(target ->
                insideArena(target.position())
            )
            .min(
                Comparator.comparingDouble(
                    target ->
                        entity.distanceToSqr(target)
                )
            );
    }

    private Optional<RobotZombie> resolvePlanTarget() {
        if (currentPlan == null) {
            return Optional.empty();
        }

        return resolveTarget(
            currentPlan.targetOwnerUuid()
        );
    }

    private Optional<RobotZombie> resolveTarget(
        UUID targetOwnerUuid
    ) {
        if (targetOwnerUuid == null) {
            return Optional.empty();
        }

        return registry
            .byOwner(targetOwnerUuid)
            .filter(target -> target != this)
            .filter(RobotController::alive)
            .flatMap(RobotController::entity)
            .filter(target ->
                entity != null
                    && target.matchId().equals(
                        entity.matchId()
                    )
            );
    }

    private boolean insideArena(Vec3 position) {
        return horizontalDistanceSqr(
            arenaCenter,
            position
        ) <= arenaRadiusSqr;
    }

    private Vec3 clampToArena(Vec3 position) {
        double dx = position.x - arenaCenter.x;
        double dz = position.z - arenaCenter.z;
        double horizontalSqr = dx * dx + dz * dz;

        if (horizontalSqr <= arenaRadiusSqr
            || horizontalSqr < 1.0E-8D) {
            return position;
        }

        double scale =
            arenaRadius / Math.sqrt(horizontalSqr);

        return new Vec3(
            arenaCenter.x + dx * scale,
            position.y,
            arenaCenter.z + dz * scale
        );
    }

    private void renderPlanIntent(TacticalPlan plan) {
        if (entity == null || entity.isRemoved()) {
            return;
        }

        Vec3 destination = switch (plan.type()) {
            case ENGAGE, CHASE ->
                resolveTarget(plan.targetOwnerUuid())
                    .map(this::entityAimPoint)
                    .orElse(null);
            case CAPTURE, DEFEND, REPOSITION ->
                clampToArena(plan.destination());
            case RETREAT ->
                retreatDestination().orElse(null);
        };

        if (destination != null) {
            renderIntentLine(destination);
        }
    }

    private void renderIntentLine(Vec3 destination) {
        if (entity == null
            || entity.isRemoved()
            || !(entity.level()
                instanceof ServerLevel level)) {
            return;
        }

        Vec3 start = entityAimPoint(entity);
        Vec3 delta = destination.subtract(start);
        double length = delta.length();

        if (length < 1.0E-4D) {
            return;
        }

        Vec3 direction = delta.normalize();
        DustParticleOptions dust =
            new DustParticleOptions(
                color.rgb(),
                INTENT_PARTICLE_SCALE
            );

        for (double traveled = 0.0D;
             traveled < length;
             traveled += INTENT_PARTICLE_SPACING) {
            Vec3 point = start.add(
                direction.scale(traveled)
            );

            level.sendParticles(
                dust,
                point.x,
                point.y,
                point.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D
            );
        }

        level.sendParticles(
            dust,
            destination.x,
            destination.y,
            destination.z,
            1,
            0.0D,
            0.0D,
            0.0D,
            0.0D
        );
    }

    private Vec3 entityAimPoint(RobotZombie robot) {
        return robot.position().add(
            0.0D,
            robot.getBbHeight() * 0.5D,
            0.0D
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

        localCombatTargetUuid = null;
        requestRedecision();
    }

    private static double calculateArenaRadius(
        ArenaConfig arena,
        Vec3 center,
        double margin
    ) {
        double maxRadius = arena.coreRadius();

        for (var spawn : arena.robotSpawns()) {
            maxRadius = Math.max(
                maxRadius,
                Math.sqrt(
                    horizontalDistanceSqr(
                        center,
                        spawn.position()
                    )
                )
            );
        }

        for (BlockPos node : arena.repositionNodes()) {
            Vec3 point = new Vec3(
                node.getX() + 0.5D,
                node.getY(),
                node.getZ() + 0.5D
            );

            maxRadius = Math.max(
                maxRadius,
                Math.sqrt(
                    horizontalDistanceSqr(
                        center,
                        point
                    )
                )
            );
        }

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
