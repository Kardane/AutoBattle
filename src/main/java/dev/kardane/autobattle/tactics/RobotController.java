package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.AutoBattleMod;
import dev.kardane.autobattle.config.ArenaConfig;
import dev.kardane.autobattle.config.RobotConfig;
import dev.kardane.autobattle.jev.DecisionTrigger;
import dev.kardane.autobattle.match.BattleTeam;
import dev.kardane.autobattle.match.MatchSession;
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
    private static final double CAPTURE_REACTION_RANGE = 2.0D;
    private static final double PATH_REQUIRED_DISTANCE = 2.0D;

    private final UUID ownerUuid;
    private final BattleTeam team;
    private final String targetId;
    private final RobotColor color;
    private final RobotRegistry registry;
    private final PlanValidityPolicy validityPolicy;
    private RobotConfig config;
    private final RobotRuntimeState runtime =
        new RobotRuntimeState();

    private Vec3 arenaCenter;
    private double arenaRadius;
    private double arenaRadiusSqr;

    private RobotZombie entity;
    private TacticalPlan currentPlan;
    private long planStartedTick = -1L;
    private long lastDecisionTick = -1L;
    private boolean decisionPending;
    private long decisionRequestedTick = -1L;
    private boolean urgentRedecisionRequested;
    private boolean redecisionRequested;
    private DecisionTrigger redecisionTrigger =
        DecisionTrigger.INITIAL;
    private long decisionGeneration;
    private UUID localCombatTargetUuid;
    private Vec3 retreatPlanDestination;
    private final TargetReachabilityTracker reachability =
        new TargetReachabilityTracker(
            3,
            40,
            3.0D
        );

    public RobotController(
        UUID ownerUuid,
        BattleTeam team,
        String targetId,
        RobotRegistry registry,
        RobotConfig config,
        ArenaConfig arena,
        PlanValidityPolicy validityPolicy
    ) {
        this.ownerUuid = Objects.requireNonNull(
            ownerUuid,
            "ownerUuid"
        );
        this.team = Objects.requireNonNull(team, "team");
        this.targetId = Objects.requireNonNull(
            targetId,
            "targetId"
        );
        this.color = team.robotColor();
        this.registry = Objects.requireNonNull(
            registry,
            "registry"
        );
        this.validityPolicy = Objects.requireNonNull(
            validityPolicy,
            "validityPolicy"
        );
        this.config = Objects.requireNonNull(
            config,
            "config"
        );

        configureArena(
            Objects.requireNonNull(arena, "arena")
        );
    }

    public void reloadConfig(
        RobotConfig config,
        ArenaConfig arena
    ) {
        this.config = Objects.requireNonNull(
            config,
            "config"
        );
        configureArena(
            Objects.requireNonNull(arena, "arena")
        );

        if (currentPlan != null
            && currentPlan.type() == TacticalPlanType.RETREAT) {
            retreatPlanDestination =
                calculateRetreatDestination().orElse(null);
        }
    }

    public UUID ownerUuid() {
        return ownerUuid;
    }

    public BattleTeam team() {
        return team;
    }

    public String targetId() {
        return targetId;
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

        if (entity.team() != team
            || !entity.targetId().equals(targetId)) {
            throw new IllegalArgumentException(
                "Robot entity team identity does not match controller."
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
        reachability.clear();
        decisionPending = false;
        decisionRequestedTick = -1L;
        urgentRedecisionRequested = false;
        decisionGeneration++;
        lastDecisionTick = -1L;
        redecisionRequested = true;
        redecisionTrigger = DecisionTrigger.RESPAWN;
        registry.reindexEntity(this);
    }

    public void resetDecisionStateForRound() {
        resetDecisionState(DecisionTrigger.INITIAL);
    }

    public void resetDecisionStateForRespawn() {
        resetDecisionState(DecisionTrigger.RESPAWN);
    }

    private void resetDecisionState(DecisionTrigger trigger) {
        clearPlan();
        reachability.clear();
        decisionPending = false;
        decisionRequestedTick = -1L;
        urgentRedecisionRequested = false;
        decisionGeneration++;
        lastDecisionTick = -1L;
        redecisionRequested = true;
        redecisionTrigger = Objects.requireNonNull(
            trigger,
            "trigger"
        );
    }

    public boolean applyPlan(
        TacticalPlan plan,
        long currentTick
    ) {
        return applyPlan(plan, currentTick, false);
    }

    public boolean applyPlan(
        TacticalPlan plan,
        long currentTick,
        boolean bypassLock
    ) {
        Objects.requireNonNull(plan, "plan");

        if (!bypassLock
            && currentPlan != null
            && currentPlan.isLocked(currentTick)) {
            return false;
        }

        currentPlan = plan;
        planStartedTick = currentTick;
        lastDecisionTick = currentTick;
        localCombatTargetUuid = plan.targetOwnerUuid();
        retreatPlanDestination =
            plan.type() == TacticalPlanType.RETREAT
                ? calculateRetreatDestination()
                    .orElse(null)
                : null;

        renderPlanIntent(plan);
        return true;
    }

    public void clearPlan() {
        currentPlan = null;
        planStartedTick = -1L;
        localCombatTargetUuid = null;
        retreatPlanDestination = null;

        if (entity != null && !entity.isRemoved()) {
            entity.setTarget(null);
            entity.getNavigation().stop();
        }
    }

    public void markDecisionRequested(
        long generation,
        long currentTick
    ) {
        decisionPending = true;
        decisionRequestedTick = currentTick;
        urgentRedecisionRequested = false;
        decisionGeneration = generation;
        redecisionRequested = false;
        redecisionTrigger = DecisionTrigger.INTERVAL;
    }

    public void markDecisionCompleted(
        long generation,
        long currentTick
    ) {
        if (generation != decisionGeneration) {
            return;
        }

        decisionPending = false;
        decisionRequestedTick = -1L;
        lastDecisionTick = currentTick;
    }

    public boolean recoverStalledDecision(
        long currentTick,
        int timeoutTicks
    ) {
        if (!decisionPending
            || decisionRequestedTick < 0L
            || timeoutTicks < 1
            || currentTick - decisionRequestedTick
                <= timeoutTicks) {
            return false;
        }

        decisionGeneration++;
        decisionPending = false;
        decisionRequestedTick = -1L;
        urgentRedecisionRequested = true;
        redecisionRequested = true;
        redecisionTrigger = DecisionTrigger.STALE_RETRY;
        return true;
    }

    public void requestRedecision() {
        requestRedecision(DecisionTrigger.OTHER);
    }

    public void requestRedecision(DecisionTrigger trigger) {
        redecisionRequested = true;
        redecisionTrigger = Objects.requireNonNull(
            trigger,
            "trigger"
        );
    }

    public void requestUrgentRedecision(
        DecisionTrigger trigger
    ) {
        decisionGeneration++;
        decisionPending = false;
        decisionRequestedTick = -1L;
        urgentRedecisionRequested = true;
        redecisionRequested = true;
        redecisionTrigger = Objects.requireNonNull(
            trigger,
            "trigger"
        );
    }

    public DecisionTrigger decisionTrigger() {
        if (redecisionRequested) {
            return redecisionTrigger;
        }

        if (lastDecisionTick < 0L) {
            return DecisionTrigger.INITIAL;
        }

        return DecisionTrigger.INTERVAL;
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
        int lockTicks,
        int debounceTicks
    ) {
        if (!alive()) {
            return false;
        }

        if (urgentRedecisionRequested) {
            return !decisionPending;
        }

        if (decisionPending) {
            return false;
        }

        if (currentPlan == null) {
            return true;
        }

        if (lastDecisionTick >= 0L
            && currentTick - lastDecisionTick
                < debounceTicks) {
            return false;
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

    public void tick(
        MatchSession match,
        long currentTick
    ) {
        Objects.requireNonNull(match, "match");

        if (!alive() || runtime.frozen()) {
            return;
        }

        if (currentPlan == null) {
            entity.setTarget(null);
            entity.getNavigation().stop();
            return;
        }

        switch (currentPlan.type()) {
            case ENGAGE -> engage(
                match,
                currentTick
            );
            case CHASE -> chase(
                match,
                currentTick
            );
            case CAPTURE ->
                capture(currentPlan.destination());
            case DEFEND ->
                defend(currentPlan.destination());
            case RETREAT -> retreat();
        }
    }

    private void engage(
        MatchSession match,
        long currentTick
    ) {
        followCombatTarget(
            match,
            currentTick,
            config.engageSpeed()
        );
    }

    private void chase(
        MatchSession match,
        long currentTick
    ) {
        followCombatTarget(
            match,
            currentTick,
            config.chaseSpeed()
        );
    }

    private void followCombatTarget(
        MatchSession match,
        long currentTick,
        double speed
    ) {
        PlanValidityResult validity =
            validityPolicy.validate(
                match,
                this,
                currentPlan,
                currentTick
            );

        if (!validity.valid()) {
            invalidateCurrentTarget(validity.status());
            return;
        }

        RobotZombie target = validity.targetEntity()
            .orElseThrow();

        entity.setTarget(target);

        boolean pathStarted = entity.getNavigation().moveTo(
            target,
            speed
        );

        boolean pathRequired =
            validity.distance() > PATH_REQUIRED_DISTANCE;

        recordApproachAttempt(
            target.ownerUuid(),
            target.position(),
            !pathRequired || pathStarted,
            currentTick
        );

        if (pathRequired
            && !pathStarted
            && isTargetTemporarilyUnreachable(
                target.ownerUuid(),
                target.position(),
                currentTick
            )) {
            invalidateCurrentTarget(
                PlanValidityStatus.TEMPORARILY_UNREACHABLE
            );
        }
    }

    private void capture(Vec3 destination) {
        Vec3 boundedDestination =
            clampToArena(destination);

        Optional<RobotZombie> nearbyThreat =
            resolveNearestEnemyNear(
                entity.position(),
                CAPTURE_REACTION_RANGE
            );

        if (nearbyThreat.isPresent()) {
            RobotZombie target = nearbyThreat.orElseThrow();
            localCombatTargetUuid = target.ownerUuid();
            entity.setTarget(target);
        } else {
            localCombatTargetUuid = null;
            entity.setTarget(null);
        }

        if (entity.position().distanceToSqr(
            boundedDestination
        ) <= square(config.positionReachedDistance())) {
            entity.getNavigation().stop();
            return;
        }

        entity.getNavigation().moveTo(
            boundedDestination.x,
            boundedDestination.y,
            boundedDestination.z,
            config.captureSpeed()
        );
    }

    private void defend(Vec3 destination) {
        Vec3 boundedDestination =
            clampToArena(destination);

        Optional<RobotZombie> intruder =
            resolveNearestEnemyNear(
                boundedDestination,
                config.defendRadius()
            );

        if (intruder.isPresent()) {
            RobotZombie target = intruder.orElseThrow();
            localCombatTargetUuid = target.ownerUuid();
            entity.setTarget(target);
            entity.getNavigation().moveTo(
                target,
                config.engageSpeed()
            );
            return;
        }

        localCombatTargetUuid = null;
        entity.setTarget(null);

        if (entity.position().distanceToSqr(
            boundedDestination
        ) > square(config.defendRadius())) {
            entity.getNavigation().moveTo(
                boundedDestination.x,
                boundedDestination.y,
                boundedDestination.z,
                config.defendSpeed()
            );
        } else {
            entity.getNavigation().stop();
        }
    }

    private void retreat() {
        entity.setTarget(null);

        if (retreatPlanDestination == null) {
            invalidateCurrentTarget();
            return;
        }

        moveToPosition(
            retreatPlanDestination,
            config.retreatSpeed(),
            square(config.positionReachedDistance()),
            true
        );
    }

    private Optional<Vec3> calculateRetreatDestination() {
        if (entity == null || entity.isRemoved()) {
            return Optional.empty();
        }

        Vec3 away = resolveNearestEnemy()
            .map(threat ->
                entity.position()
                    .subtract(threat.position())
            )
            .orElseGet(() ->
                entity.position()
                    .subtract(arenaCenter)
            );

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

        return Optional.of(
            clampToArena(destination)
        );
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
                requestRedecision(
                    DecisionTrigger.PLAN_COMPLETED
                );
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
            .filter(controller ->
                team.isEnemy(controller.team())
            )
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

    private Optional<RobotZombie> resolveNearestEnemyNear(
        Vec3 center,
        double radius
    ) {
        if (entity == null) {
            return Optional.empty();
        }

        double radiusSqr = square(radius);

        return registry.alive().stream()
            .filter(controller -> controller != this)
            .filter(controller ->
                team.isEnemy(controller.team())
            )
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
            .filter(target ->
                target.position().distanceToSqr(center)
                    <= radiusSqr
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
            .filter(target -> team.isEnemy(target.team()))
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
        return validityPolicy.insideArena(position);
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

    public Optional<Vec3> positionSnapshot() {
        if (entity == null || entity.isRemoved()) {
            return Optional.empty();
        }

        return Optional.of(entity.position());
    }

    public Optional<Vec3> targetPositionSnapshot() {
        if (entity == null || entity.isRemoved()) {
            return Optional.empty();
        }

        if (localCombatTargetUuid != null) {
            Optional<Vec3> local = resolveTarget(
                localCombatTargetUuid
            ).map(RobotZombie::position);

            if (local.isPresent()) {
                return local;
            }
        }

        if (currentPlan != null
            && currentPlan.targetOwnerUuid() != null) {
            return resolveTarget(
                currentPlan.targetOwnerUuid()
            ).map(RobotZombie::position);
        }

        return Optional.empty();
    }

    public Optional<Vec3> destinationSnapshot() {
        if (currentPlan == null) {
            return Optional.empty();
        }

        return switch (currentPlan.type()) {
            case CAPTURE, DEFEND ->
                Optional.of(
                    clampToArena(
                        currentPlan.destination()
                    )
                );
            case RETREAT ->
                Optional.ofNullable(
                    retreatPlanDestination
                );
            case ENGAGE, CHASE ->
                Optional.empty();
        };
    }

    public double distanceToCoreSnapshot() {
        if (entity == null || entity.isRemoved()) {
            return Double.NaN;
        }

        return entity.position()
            .distanceTo(arenaCenter);
    }

    public double distanceToTargetSnapshot() {
        if (entity == null || entity.isRemoved()) {
            return Double.NaN;
        }

        return targetPositionSnapshot()
            .map(entity.position()::distanceTo)
            .orElse(Double.NaN);
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
            case CAPTURE, DEFEND ->
                clampToArena(plan.destination());
            case RETREAT ->
                retreatPlanDestination;
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

    boolean isTargetTemporarilyUnreachable(
        UUID targetOwnerUuid,
        Vec3 targetPosition,
        long currentTick
    ) {
        return reachability.isTemporarilyUnreachable(
            targetOwnerUuid,
            targetPosition,
            currentTick
        );
    }

    private void recordApproachAttempt(
        UUID targetOwnerUuid,
        Vec3 targetPosition,
        boolean success,
        long currentTick
    ) {
        reachability.recordAttempt(
            targetOwnerUuid,
            targetPosition,
            success,
            currentTick
        );
    }

    private void invalidateCurrentTarget() {
        invalidateCurrentTarget(
            PlanValidityStatus.TARGET_MISSING
        );
    }

    private void invalidateCurrentTarget(
        PlanValidityStatus status
    ) {
        String invalidPlanId = currentPlan == null
            ? null
            : currentPlan.externalId();

        if (entity != null && !entity.isRemoved()) {
            entity.setTarget(null);
            entity.getNavigation().stop();
        }

        clearPlan();
        requestRedecision(
            DecisionTrigger.TARGET_INVALIDATED
        );

        AutoBattleMod.LOGGER.debug(
            "Invalidated robot plan owner={} plan={} reason={}",
            ownerUuid,
            invalidPlanId,
            status
        );
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

    private void configureArena(ArenaConfig arena) {
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

    private static double horizontalDistanceSqr(
        Vec3 first,
        Vec3 second
    ) {
        double dx = first.x - second.x;
        double dz = first.z - second.z;
        return dx * dx + dz * dz;
    }
}
