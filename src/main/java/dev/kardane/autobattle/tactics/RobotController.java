package dev.kardane.autobattle.tactics;

import dev.kardane.autobattle.AutoBattleConstants;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RobotController {
    private static final double INTENT_PARTICLE_SPACING = 0.2D;
    private static final float INTENT_PARTICLE_SCALE = 0.9F;
    private static final double ASSIST_REACTION_RANGE = 4.0D;
    private static final double ASSIST_MIN_DISTANCE = 2.0D;
    private static final double ASSIST_MAX_DISTANCE = 4.0D;
    private static final double ASSIST_PREFERRED_DISTANCE = 3.0D;
    private static final double PATH_REQUIRED_DISTANCE = 2.0D;
    private static final double DETOUR_FORWARD_DISTANCE = 2.0D;
    private static final double DETOUR_LATERAL_DISTANCE = 3.0D;
    private static final int RETREAT_REEVALUATE_TICKS = 15;
    private static final int RETREAT_BLOCKED_DESTINATION_TICKS = 40;

    private final UUID ownerUuid;
    private final BattleTeam team;
    private final String targetId;
    private final RobotColor color;
    private final RobotRegistry registry;
    private final PlanValidityPolicy validityPolicy;
    private final RetreatPlanner retreatPlanner;
    private RobotConfig config;
    private final RobotRuntimeState runtime =
        new RobotRuntimeState();

    private Vec3 arenaCenter;
    private double arenaRadius;
    private double arenaRadiusSqr;

    private RobotZombie entity;
    private TacticalPlan currentPlan;
    private PlanSource currentPlanSource;
    private long planStartedTick = -1L;
    private long lastDecisionTick = -1L;
    private long decisionRetryNotBeforeTick = -1L;
    private boolean decisionPending;
    private long decisionRequestedTick = -1L;
    private boolean urgentRedecisionRequested;
    private boolean redecisionRequested;
    private DecisionTrigger redecisionTrigger =
        DecisionTrigger.INITIAL;
    private long decisionGeneration;
    private UUID localCombatTargetUuid;
    private Vec3 retreatPlanDestination;
    private RetreatPlanner.RetreatChoice retreatChoice;
    private long nextRetreatEvaluationTick = -1L;
    private long holdUntilTick = -1L;
    private boolean holdCompletionRequested;
    private final List<BlockedRetreatDestination>
        blockedRetreatDestinations = new ArrayList<>();
    private final TargetReachabilityTracker reachability =
        new TargetReachabilityTracker(
            3,
            40,
            3.0D
        );
    private final PlanReachabilityTracker planReachability =
        new PlanReachabilityTracker(
            40,
            3.0D
        );
    private final MovementRecoveryTracker movementRecovery =
        new MovementRecoveryTracker(
            10,
            25,
            0.25D,
            1.0D,
            1.0D
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
        this.retreatPlanner = new RetreatPlanner(
            validityPolicy,
            config
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
        retreatPlanner.reload(config);
        configureArena(
            Objects.requireNonNull(arena, "arena")
        );

        if (currentPlan != null
            && currentPlan.type() == TacticalPlanType.RETREAT) {
            retreatPlanDestination = null;
            retreatChoice = null;
            nextRetreatEvaluationTick = 0L;
            blockedRetreatDestinations.clear();
            movementRecovery.reset();
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

    public boolean isHoldExpired(long currentTick) {
        return currentPlan != null
            && currentPlan.type() == TacticalPlanType.HOLD
            && holdUntilTick >= 0L
            && currentTick >= holdUntilTick;
    }

    public boolean isRetreatSafe(MatchSession match) {
        Objects.requireNonNull(match, "match");

        return alive()
            && retreatPlanner.sufficientlySafe(
                match,
                this
            );
    }

    public Optional<PlanSource> currentPlanSource() {
        return Optional.ofNullable(currentPlanSource);
    }

    public boolean hasProvisionalPlan() {
        return currentPlan != null
            && currentPlanSource == PlanSource.PROVISIONAL;
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
        entity.setDisplayedPlan(
            currentPlan == null
                ? null
                : currentPlan.type()
        );
        runtime.markSpawned(currentTick);
        registry.reindexEntity(this);
    }

    public void detachEntity() {
        clearPlan();
        entity = null;
        localCombatTargetUuid = null;
        reachability.clear();
        planReachability.clear();
        movementRecovery.reset();
        decisionPending = false;
        decisionRequestedTick = -1L;
        urgentRedecisionRequested = false;
        decisionGeneration++;
        lastDecisionTick = -1L;
        decisionRetryNotBeforeTick = -1L;
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
        planReachability.clear();
        movementRecovery.reset();
        decisionPending = false;
        decisionRequestedTick = -1L;
        urgentRedecisionRequested = false;
        decisionGeneration++;
        lastDecisionTick = -1L;
        decisionRetryNotBeforeTick = -1L;
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
        return applyPlan(
            plan,
            currentTick,
            false,
            PlanSource.AI
        );
    }

    public boolean applyPlan(
        TacticalPlan plan,
        long currentTick,
        boolean bypassLock
    ) {
        return applyPlan(
            plan,
            currentTick,
            bypassLock,
            PlanSource.AI
        );
    }

    public boolean applyPlan(
        TacticalPlan plan,
        long currentTick,
        boolean bypassLock,
        PlanSource source
    ) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(source, "source");

        boolean replacingProvisional =
            currentPlanSource == PlanSource.PROVISIONAL;

        if (!bypassLock
            && !replacingProvisional
            && currentPlan != null
            && currentPlan.isLocked(currentTick)) {
            return false;
        }

        currentPlan = plan;
        currentPlanSource = source;
        movementRecovery.reset();
        planStartedTick = currentTick;

        if (source != PlanSource.PROVISIONAL) {
            lastDecisionTick = currentTick;
            decisionRetryNotBeforeTick = -1L;
        }

        localCombatTargetUuid = plan.targetOwnerUuid();
        retreatPlanDestination = null;
        retreatChoice = null;
        nextRetreatEvaluationTick =
            plan.type() == TacticalPlanType.RETREAT
                ? currentTick
                : -1L;
        holdUntilTick =
            plan.type() == TacticalPlanType.HOLD
                ? currentTick
                    + AutoBattleConstants.HOLD_DURATION_TICKS
                : -1L;
        holdCompletionRequested = false;
        blockedRetreatDestinations.clear();

        if (entity != null && !entity.isRemoved()) {
            entity.setDisplayedPlan(plan.type());
        }

        renderPlanIntent(plan);
        return true;
    }

    public boolean applyProvisionalPlan(
        TacticalPlan plan,
        long currentTick
    ) {
        if (currentPlan != null) {
            return false;
        }

        return applyPlan(
            plan,
            currentTick,
            true,
            PlanSource.PROVISIONAL
        );
    }

    public void clearPlan() {
        currentPlan = null;
        currentPlanSource = null;
        planStartedTick = -1L;
        localCombatTargetUuid = null;
        retreatPlanDestination = null;
        retreatChoice = null;
        nextRetreatEvaluationTick = -1L;
        holdUntilTick = -1L;
        holdCompletionRequested = false;
        blockedRetreatDestinations.clear();
        movementRecovery.reset();

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
        decisionRetryNotBeforeTick = -1L;
        urgentRedecisionRequested = false;
        decisionGeneration = generation;
        redecisionRequested = false;
        redecisionTrigger = DecisionTrigger.INTERVAL;
    }

    public void markDecisionCompleted(
        long generation,
        long currentTick
    ) {
        markDecisionCompleted(
            generation,
            currentTick,
            true
        );
    }

    public void markDecisionCompleted(
        long generation,
        long currentTick,
        boolean recordDecisionTick
    ) {
        if (generation != decisionGeneration) {
            return;
        }

        decisionPending = false;
        decisionRequestedTick = -1L;

        if (recordDecisionTick) {
            lastDecisionTick = currentTick;
        }
    }

    public void deferDecisionRetryUntil(long tick) {
        decisionRetryNotBeforeTick = Math.max(
            decisionRetryNotBeforeTick,
            tick
        );
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
        decisionRetryNotBeforeTick = -1L;
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

        if (decisionRetryNotBeforeTick >= 0L
            && currentTick < decisionRetryNotBeforeTick) {
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

        if (currentPlanSource != PlanSource.PROVISIONAL
            && currentTick < lockEndTick) {
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

        if (!insideArena(entity.position())) {
            entity.setPos(clampToArena(entity.position()));
            entity.getNavigation().stop();
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
                capture(
                    match,
                    currentPlan.destination(),
                    currentTick
                );
            case DEFEND ->
                defend(
                    currentPlan.destination(),
                    currentTick
                );
            case ASSIST -> assist(
                match,
                currentTick
            );
            case HOLD -> holdPosition(
                currentTick
            );
            case RETREAT -> retreat(
                match,
                currentTick
            );
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

        boolean inAttackRange =
            validity.distance() <= PATH_REQUIRED_DISTANCE;

        if (!navigateWithRecovery(
            target.position(),
            speed,
            inAttackRange,
            currentTick
        )) {
            reachability.excludeNow(
                target.ownerUuid(),
                target.position(),
                currentTick
            );
            abandonCurrentMovement(
                target.position(),
                DecisionTrigger.MOVEMENT_FAILED,
                PlanValidityStatus.TEMPORARILY_UNREACHABLE
            );
        }
    }

    private void capture(
        MatchSession match,
        Vec3 destination,
        long currentTick
    ) {
        Vec3 boundedDestination =
            clampToArena(destination);

        Optional<RobotZombie> contestingEnemy =
            resolveNearestEnemyInsideCore(
                match,
                currentTick
            );

        if (contestingEnemy.isPresent()) {
            RobotZombie target =
                contestingEnemy.orElseThrow();

            localCombatTargetUuid =
                target.ownerUuid();
            entity.setTarget(target);

            boolean inAttackRange =
                entity.distanceTo(target)
                    <= PATH_REQUIRED_DISTANCE;

            if (!navigateWithRecovery(
                target.position(),
                config.engageSpeed(),
                inAttackRange,
                currentTick
            )) {
                reachability.excludeNow(
                    target.ownerUuid(),
                    target.position(),
                    currentTick
                );
                stopLocalMovement();
                movementRecovery.reset();
            }
            return;
        }

        localCombatTargetUuid = null;
        entity.setTarget(null);

        if (match.core().isInside(entity)) {
            entity.getNavigation().stop();
            movementRecovery.reset();
            return;
        }

        if (!navigateWithRecovery(
            boundedDestination,
            config.captureSpeed(),
            false,
            currentTick
        )) {
            suppressCurrentPlanAndRedecide(
                boundedDestination,
                currentTick
            );
        }
    }

    private Optional<RobotZombie> resolveNearestEnemyInsideCore(
        MatchSession match,
        long currentTick
    ) {
        if (entity == null) {
            return Optional.empty();
        }

        return registry.alive().stream()
            .filter(controller -> controller != this)
            .filter(controller ->
                team.isEnemy(controller.team())
            )
            .flatMap(controller ->
                controller.entity().stream()
            )
            .filter(target ->
                target.matchId().equals(entity.matchId())
            )
            .filter(match.core()::isInside)
            .filter(target ->
                !isTargetTemporarilyUnreachable(
                    target.ownerUuid(),
                    target.position(),
                    currentTick
                )
            )
            .min(
                Comparator.comparingDouble(
                    target ->
                        entity.distanceToSqr(target)
                )
            );
    }

    private void defend(
        Vec3 destination,
        long currentTick
    ) {
        Vec3 boundedDestination =
            clampToArena(destination);

        Optional<RobotZombie> intruder =
            resolveNearestEnemyNear(
                boundedDestination,
                config.defendRadius(),
                currentTick,
                true
            );

        if (intruder.isPresent()) {
            RobotZombie target = intruder.orElseThrow();
            localCombatTargetUuid = target.ownerUuid();
            entity.setTarget(target);

            boolean inAttackRange =
                entity.distanceTo(target)
                    <= PATH_REQUIRED_DISTANCE;

            if (!navigateWithRecovery(
                target.position(),
                config.engageSpeed(),
                inAttackRange,
                currentTick
            )) {
                reachability.excludeNow(
                    target.ownerUuid(),
                    target.position(),
                    currentTick
                );
                localCombatTargetUuid = null;
                entity.setTarget(null);
                entity.getNavigation().stop();
                movementRecovery.reset();
            }
            return;
        }

        localCombatTargetUuid = null;
        entity.setTarget(null);

        boolean holdingPosition =
            entity.position().distanceToSqr(
                boundedDestination
            ) <= square(config.defendRadius());

        if (!navigateWithRecovery(
            boundedDestination,
            config.defendSpeed(),
            holdingPosition,
            currentTick
        )) {
            suppressCurrentPlanAndRedecide(
                boundedDestination,
                currentTick
            );
        }
    }

    private void holdPosition(long currentTick) {
        Optional<RobotZombie> nearbyThreat =
            resolveNearestEnemyNear(
                entity.position(),
                config.holdReactionRange(),
                currentTick,
                false
            );

        if (nearbyThreat.isPresent()) {
            RobotZombie target = nearbyThreat.orElseThrow();
            localCombatTargetUuid = target.ownerUuid();
            entity.setTarget(target);

            boolean inAttackRange =
                entity.distanceTo(target)
                    <= PATH_REQUIRED_DISTANCE;

            if (!navigateWithRecovery(
                target.position(),
                config.engageSpeed(),
                inAttackRange,
                currentTick
            )) {
                stopLocalMovement();
            }
        } else {
            stopLocalMovement();
            movementRecovery.reset();
        }

        if (holdUntilTick >= 0L
            && currentTick >= holdUntilTick
            && !holdCompletionRequested) {
            holdCompletionRequested = true;
            requestRedecision(
                DecisionTrigger.HOLD_COMPLETE
            );
        }
    }

    private void assist(
        MatchSession match,
        long currentTick
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

        RobotController ally = match.robots()
            .byOwner(currentPlan.targetOwnerUuid())
            .orElse(null);

        if (ally == null || !ally.alive()) {
            invalidateCurrentTarget(
                PlanValidityStatus.TARGET_DEAD
            );
            return;
        }

        TacticalPlan allyPlan = ally.currentPlan()
            .orElse(null);

        if (allyPlan != null
            && allyPlan.type() == TacticalPlanType.RETREAT) {
            stopLocalMovement();
            requestRedecision(
                DecisionTrigger.TARGET_INVALIDATED
            );
            return;
        }

        if (allyPlan != null
            && (allyPlan.type() == TacticalPlanType.ENGAGE
                || allyPlan.type() == TacticalPlanType.CHASE)) {
            assistCombat(
                match,
                ally,
                allyPlan.type(),
                currentTick
            );
            return;
        }

        moveNearAlly(
            ally,
            currentTick
        );
    }

    private void assistCombat(
        MatchSession match,
        RobotController ally,
        TacticalPlanType allyPlanType,
        long currentTick
    ) {
        UUID targetOwnerUuid = ally
            .combatTargetOwnerSnapshot()
            .orElse(null);

        if (targetOwnerUuid == null) {
            moveNearAlly(ally, currentTick);
            return;
        }

        PlanValidityResult targetValidity =
            validityPolicy.validateTarget(
                match,
                this,
                targetOwnerUuid,
                allyPlanType,
                currentTick
            );

        if (!targetValidity.valid()) {
            moveNearAlly(ally, currentTick);
            return;
        }

        RobotZombie target = targetValidity.targetEntity()
            .orElseThrow();
        localCombatTargetUuid = target.ownerUuid();
        entity.setTarget(target);

        boolean inAttackRange =
            targetValidity.distance() <= PATH_REQUIRED_DISTANCE;

        if (!navigateWithRecovery(
            target.position(),
            allyPlanType == TacticalPlanType.CHASE
                ? config.chaseSpeed()
                : config.engageSpeed(),
            inAttackRange,
            currentTick
        )) {
            moveNearAlly(ally, currentTick);
        }
    }

    private void moveNearAlly(
        RobotController ally,
        long currentTick
    ) {
        RobotZombie allyEntity = ally.entity().orElse(null);

        if (allyEntity == null
            || allyEntity.isRemoved()
            || !allyEntity.isAlive()) {
            invalidateCurrentTarget(
                PlanValidityStatus.TARGET_DEAD
            );
            return;
        }

        Optional<RobotZombie> nearbyThreat =
            resolveNearestEnemyNear(
                entity.position(),
                ASSIST_REACTION_RANGE,
                currentTick,
                false
            );

        if (nearbyThreat.isPresent()) {
            RobotZombie target = nearbyThreat.orElseThrow();
            localCombatTargetUuid = target.ownerUuid();
            entity.setTarget(target);

            boolean inAttackRange =
                entity.distanceTo(target)
                    <= PATH_REQUIRED_DISTANCE;

            if (!navigateWithRecovery(
                target.position(),
                config.engageSpeed(),
                inAttackRange,
                currentTick
            )) {
                stopLocalMovement();
            }
            return;
        }

        localCombatTargetUuid = null;
        entity.setTarget(null);

        double allyDistance = entity.distanceTo(allyEntity);
        boolean holdingPosition =
            allyDistance >= ASSIST_MIN_DISTANCE
                && allyDistance <= ASSIST_MAX_DISTANCE;
        Vec3 destination = supportPosition(allyEntity);

        if (!navigateWithRecovery(
            destination,
            config.captureSpeed(),
            holdingPosition,
            currentTick
        )) {
            suppressCurrentPlanAndRedecide(
                destination,
                currentTick
            );
        }
    }

    private Vec3 supportPosition(RobotZombie ally) {
        Vec3 offset = entity.position().subtract(
            ally.position()
        );
        Vec3 horizontal = new Vec3(
            offset.x,
            0.0D,
            offset.z
        );

        if (horizontal.lengthSqr() < 1.0E-4D) {
            horizontal = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            horizontal = horizontal.normalize();
        }

        return clampToArena(
            ally.position().add(
                horizontal.scale(ASSIST_PREFERRED_DISTANCE)
            )
        );
    }

    private void stopLocalMovement() {
        localCombatTargetUuid = null;

        if (entity != null && !entity.isRemoved()) {
            entity.setTarget(null);
            entity.getNavigation().stop();
        }
    }

    private void retreat(
        MatchSession match,
        long currentTick
    ) {
        entity.setTarget(null);
        pruneBlockedRetreatDestinations(currentTick);

        if (isRetreatSafe(match)) {
            completeRetreatIntoHold(currentTick);
            return;
        }

        boolean arrived =
            retreatPlanDestination != null
                && entity.position().distanceToSqr(
                    retreatPlanDestination
                ) <= square(
                    config.positionReachedDistance()
                );

        boolean shouldReevaluate =
            retreatChoice == null
                || currentTick
                    >= nextRetreatEvaluationTick
                || arrived;

        if (shouldReevaluate
            && !refreshRetreatDestination(
                match,
                currentTick,
                arrived
            )) {
            suppressRetreatAndRedecide(
                currentTick,
                DecisionTrigger.RETREAT_UNAVAILABLE
            );
            return;
        }

        if (retreatPlanDestination == null) {
            suppressRetreatAndRedecide(
                currentTick,
                DecisionTrigger.RETREAT_UNAVAILABLE
            );
            return;
        }

        if (!navigateWithRecovery(
            retreatPlanDestination,
            config.retreatSpeed(),
            false,
            currentTick
        )) {
            blockRetreatDestination(
                retreatPlanDestination,
                currentTick
            );

            if (!refreshRetreatDestination(
                match,
                currentTick,
                true
            )) {
                suppressRetreatAndRedecide(
                    currentTick,
                    DecisionTrigger.MOVEMENT_FAILED
                );
            }
        }
    }

    private boolean refreshRetreatDestination(
        MatchSession match,
        long currentTick,
        boolean forceSwitch
    ) {
        Optional<RetreatPlanner.RetreatChoice> planned =
            retreatPlanner.choose(
                match,
                this,
                retreatChoice,
                activeBlockedRetreatDestinations(
                    currentTick
                ),
                forceSwitch
            );

        nextRetreatEvaluationTick =
            currentTick + RETREAT_REEVALUATE_TICKS;

        if (planned.isEmpty()) {
            return false;
        }

        RetreatPlanner.RetreatChoice selected =
            planned.orElseThrow();

        boolean changed =
            retreatPlanDestination == null
                || retreatPlanDestination.distanceToSqr(
                    selected.destination()
                ) >= 0.25D;

        retreatChoice = selected;
        retreatPlanDestination =
            selected.destination();

        if (changed) {
            movementRecovery.reset();
            renderIntentLine(
                retreatPlanDestination
            );

            AutoBattleMod.LOGGER.debug(
                "Updated retreat destination owner={} destination={} score={} enemyDistance={} pathEnemyDistance={} edgeMargin={}",
                ownerUuid,
                retreatPlanDestination,
                selected.score(),
                selected.minimumEnemyDistance(),
                selected.pathMinimumEnemyDistance(),
                selected.edgeMargin()
            );
        }

        return true;
    }

    private void completeRetreatIntoHold(
        long currentTick
    ) {
        TacticalPlan hold = TacticalPlan.hold(
            currentTick,
            0L
        );

        applyPlan(
            hold,
            currentTick,
            true,
            PlanSource.FALLBACK
        );

        stopLocalMovement();

        requestUrgentRedecision(
            DecisionTrigger.RETREAT_SAFE
        );

        AutoBattleMod.LOGGER.debug(
            "Retreat completed into HOLD owner={}",
            ownerUuid
        );
    }

    private void suppressRetreatAndRedecide(
        long currentTick,
        DecisionTrigger trigger
    ) {
        planReachability.exclude(
            "RETREAT",
            null,
            currentTick
        );

        abandonCurrentMovement(
            retreatPlanDestination,
            trigger,
            PlanValidityStatus.TEMPORARILY_UNREACHABLE
        );
    }

    private void blockRetreatDestination(
        Vec3 destination,
        long currentTick
    ) {
        blockedRetreatDestinations.add(
            new BlockedRetreatDestination(
                destination,
                currentTick
                    + RETREAT_BLOCKED_DESTINATION_TICKS
            )
        );
        pruneBlockedRetreatDestinations(currentTick);
    }

    private List<Vec3> activeBlockedRetreatDestinations(
        long currentTick
    ) {
        pruneBlockedRetreatDestinations(currentTick);

        return blockedRetreatDestinations.stream()
            .map(
                BlockedRetreatDestination::destination
            )
            .toList();
    }

    private void pruneBlockedRetreatDestinations(
        long currentTick
    ) {
        blockedRetreatDestinations.removeIf(
            blocked ->
                currentTick >= blocked.untilTick()
        );
    }

    private boolean navigateWithRecovery(
        Vec3 destination,
        double speed,
        boolean allowedToStop,
        long currentTick
    ) {
        Vec3 boundedDestination =
            clampToArena(destination);

        if (allowedToStop) {
            entity.getNavigation().stop();
        }

        MovementRecoveryTracker.Action action =
            movementRecovery.evaluate(
                entity.position(),
                boundedDestination,
                allowedToStop,
                entity.getNavigation().isDone(),
                currentTick
            );

        while (action != MovementRecoveryTracker.Action.NONE) {
            if (action
                == MovementRecoveryTracker.Action.ABANDON) {
                return false;
            }

            Vec3 pathDestination =
                switch (action) {
                    case REFRESH_DIRECT ->
                        boundedDestination;
                    case TRY_LEFT_DETOUR ->
                        calculateDetourWaypoint(
                            boundedDestination,
                            true
                        );
                    case TRY_RIGHT_DETOUR ->
                        calculateDetourWaypoint(
                            boundedDestination,
                            false
                        );
                    case NONE, ABANDON ->
                        throw new IllegalStateException(
                            "Unexpected navigation action: "
                                + action
                        );
                };

            boolean started =
                entity.getNavigation().moveTo(
                    pathDestination.x,
                    pathDestination.y,
                    pathDestination.z,
                    speed
                );

            AutoBattleMod.LOGGER.debug(
                "Movement recovery owner={} plan={} action={} pathStarted={} destination={}",
                ownerUuid,
                currentPlan == null
                    ? null
                    : currentPlan.externalId(),
                action,
                started,
                pathDestination
            );

            action = movementRecovery.recordPathAttempt(
                action,
                started,
                action == MovementRecoveryTracker.Action
                    .REFRESH_DIRECT
                    ? null
                    : pathDestination,
                currentTick
            );
        }

        return true;
    }

    private Vec3 calculateDetourWaypoint(
        Vec3 destination,
        boolean left
    ) {
        Vec3 current = entity.position();
        Vec3 toward = new Vec3(
            destination.x - current.x,
            0.0D,
            destination.z - current.z
        );

        if (toward.lengthSqr() < 1.0E-4D) {
            toward = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            toward = toward.normalize();
        }

        Vec3 lateral = left
            ? new Vec3(-toward.z, 0.0D, toward.x)
            : new Vec3(toward.z, 0.0D, -toward.x);

        return clampToArena(
            current
                .add(
                    toward.scale(
                        DETOUR_FORWARD_DISTANCE
                    )
                )
                .add(
                    lateral.scale(
                        DETOUR_LATERAL_DISTANCE
                    )
                )
        );
    }

    private void suppressCurrentPlanAndRedecide(
        Vec3 destination,
        long currentTick
    ) {
        if (currentPlan != null) {
            planReachability.exclude(
                currentPlan.externalId(),
                destination,
                currentTick
            );
        }

        abandonCurrentMovement(
            destination,
            DecisionTrigger.MOVEMENT_FAILED,
            PlanValidityStatus.TEMPORARILY_UNREACHABLE
        );
    }

    private void abandonCurrentMovement(
        Vec3 destination,
        DecisionTrigger trigger,
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
        requestRedecision(trigger);

        AutoBattleMod.LOGGER.debug(
            "Abandoned robot movement owner={} plan={} reason={} destination={}",
            ownerUuid,
            invalidPlanId,
            status,
            destination
        );
    }

    private Optional<RobotZombie> resolveNearestEnemyNear(
        Vec3 center,
        double radius,
        long currentTick,
        boolean excludeUnreachable
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
                !excludeUnreachable
                    || !isTargetTemporarilyUnreachable(
                        target.ownerUuid(),
                        target.position(),
                        currentTick
                    )
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

    private Optional<RobotZombie> resolveOwnerTarget(
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

    public Optional<UUID> combatTargetOwnerSnapshot() {
        if (entity == null || entity.isRemoved()) {
            return Optional.empty();
        }

        if (entity.getTarget() instanceof RobotZombie target
            && target.isAlive()
            && !target.isRemoved()
            && target.matchId().equals(entity.matchId())
            && team.isEnemy(target.team())) {
            return Optional.of(target.ownerUuid());
        }

        if (localCombatTargetUuid != null
            && resolveTarget(localCombatTargetUuid).isPresent()) {
            return Optional.of(localCombatTargetUuid);
        }

        if (currentPlan != null
            && (currentPlan.type() == TacticalPlanType.ENGAGE
                || currentPlan.type() == TacticalPlanType.CHASE)
            && resolveTarget(
                currentPlan.targetOwnerUuid()
            ).isPresent()) {
            return Optional.of(
                currentPlan.targetOwnerUuid()
            );
        }

        return Optional.empty();
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
            Optional<RobotZombie> target =
                currentPlan.type() == TacticalPlanType.ASSIST
                    ? resolveOwnerTarget(
                        currentPlan.targetOwnerUuid()
                    )
                    : resolveTarget(
                        currentPlan.targetOwnerUuid()
                    );

            return target.map(RobotZombie::position);
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
            case ENGAGE, CHASE, ASSIST, HOLD ->
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
            case ASSIST ->
                resolveOwnerTarget(plan.targetOwnerUuid())
                    .map(this::entityAimPoint)
                    .orElse(null);
            case HOLD -> null;
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

    boolean isPlanTemporarilyUnreachable(
        TacticalPlan plan,
        long currentTick
    ) {
        if (plan == null
            || plan.type() == TacticalPlanType.ENGAGE
            || plan.type() == TacticalPlanType.CHASE) {
            return false;
        }

        return planReachability.isExcluded(
            plan.externalId(),
            plan.destination(),
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


    private record BlockedRetreatDestination(
        Vec3 destination,
        long untilTick
    ) {
        BlockedRetreatDestination {
            Objects.requireNonNull(
                destination,
                "destination"
            );
        }
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
