package dev.kardane.autobattle.jev;

import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.match.MatchPhase;
import dev.kardane.autobattle.match.MatchSession;
import dev.kardane.autobattle.match.PlayerSlot;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.tactics.RobotController;
import dev.kardane.autobattle.tactics.TacticalPlan;
import dev.kardane.autobattle.tactics.ValidPlanFactory;
import net.minecraft.server.MinecraftServer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class JevDecisionService {
    private JevClient client;
    private final RobotStateSerializer serializer;
    private final ValidPlanFactory validPlanFactory;
    private final PlanExecutor planExecutor;
    private final DecisionLogRepository logs;
    private final DecisionComposer composer =
        new DecisionComposer();
    private final DecisionIntervalStagger intervalStagger =
        new DecisionIntervalStagger();
    private int inFlightRequests;
    private AutoBattleConfig config;

    public JevDecisionService(
        JevClient client,
        RobotStateSerializer serializer,
        ValidPlanFactory validPlanFactory,
        PlanExecutor planExecutor,
        DecisionLogRepository logs,
        AutoBattleConfig config
    ) {
        this.client = Objects.requireNonNull(client, "client");
        this.serializer = Objects.requireNonNull(
            serializer,
            "serializer"
        );
        this.validPlanFactory = Objects.requireNonNull(
            validPlanFactory,
            "validPlanFactory"
        );
        this.planExecutor = Objects.requireNonNull(
            planExecutor,
            "planExecutor"
        );
        this.logs = Objects.requireNonNull(logs, "logs");
        this.config = Objects.requireNonNull(config, "config");
    }

    public void reload(
        JevClient client,
        AutoBattleConfig config
    ) {
        this.client = Objects.requireNonNull(client, "client");
        this.config = Objects.requireNonNull(config, "config");
        this.validPlanFactory.reload(config);
        this.serializer.reloadConfig(config.robot());
    }

    public DecisionLogRepository logs() {
        return logs;
    }

    public void tick(
        MinecraftServer server,
        MatchSession match,
        long currentTick
    ) {
        if (match.phase() != MatchPhase.ROUND_ACTIVE) {
            return;
        }

        for (RobotController controller :
            match.robots().all()) {
            if (!controller.shouldRequestDecision(
                currentTick,
                config.decisionIntervalTicks(),
                config.decisionLockTicks(),
                config.decisionDebounceTicks()
            )) {
                continue;
            }

            if (controller.decisionTrigger()
                    == DecisionTrigger.INTERVAL
                && controller.currentPlan().isPresent()
                && !intervalStagger.eligible(
                    match,
                    controller.ownerUuid(),
                    currentTick,
                    config.decisionIntervalTicks()
                )) {
                continue;
            }

            requestDecision(
                server,
                match,
                controller,
                currentTick
            );
        }
    }

    public void requestDecision(
        MinecraftServer server,
        MatchSession match,
        RobotController controller,
        long currentTick
    ) {
        if (!controller.alive()
            || controller.hasPendingDecision()
            || match.phase() != MatchPhase.ROUND_ACTIVE) {
            return;
        }

        List<TacticalPlan> candidates =
            validPlanFactory.create(
                match,
                controller,
                currentTick
            );

        if (candidates.isEmpty()) {
            return;
        }

        Map<String, TacticalPlan> byId =
            indexCandidates(candidates);

        List<String> validPlanIds =
            List.copyOf(byId.keySet());

        PlayerSlot slot = match.player(
            controller.ownerUuid()
        ).orElse(null);

        UUID entityUuid = controller.entityUuid()
            .orElse(null);

        if (slot == null
            || slot.doctrine().isEmpty()
            || entityUuid == null) {
            return;
        }

        RobotDecisionSnapshot snapshot =
            serializer.snapshot(
                match,
                controller,
                currentTick
            );

        long generation = controller.nextDecisionGeneration();
        DecisionTrigger trigger = controller.decisionTrigger();
        int inFlightAtRequest = ++inFlightRequests;

        DecisionContext context = new DecisionContext(
            match.matchId(),
            match.currentRound(),
            controller.ownerUuid(),
            entityUuid,
            slot.doctrine().orElseThrow().version(),
            generation,
            currentTick,
            trigger,
            inFlightAtRequest
        );

        DecisionRequest request = new DecisionRequest(
            context,
            snapshot,
            validPlanIds
        );

        controller.markDecisionRequested(generation);

        java.util.concurrent.CompletableFuture<DecisionResponse> future;

        try {
            future = client.decide(request);
        } catch (Throwable error) {
            inFlightRequests = Math.max(
                0,
                inFlightRequests - 1
            );

            applyResponse(
                match,
                controller,
                request,
                null,
                error,
                currentTick
            );
            return;
        }

        future.orTimeout(
                config.jevTimeoutMs(),
                TimeUnit.MILLISECONDS
            )
            .whenComplete((response, error) ->
                server.execute(() -> {
                    inFlightRequests = Math.max(
                        0,
                        inFlightRequests - 1
                    );

                    applyResponse(
                        match,
                        controller,
                        request,
                        response,
                        error,
                        server.getTickCount()
                    );
                })
            );
    }

    private DecisionApplyResult applyResponse(
        MatchSession match,
        RobotController controller,
        DecisionRequest request,
        DecisionResponse response,
        Throwable error,
        long currentTick
    ) {
        DecisionContext context = request.context();
        String previousPlanId = controller.currentPlan()
            .map(TacticalPlan::externalId)
            .orElse(null);

        if (!match.matchId().equals(context.matchId())) {
            return logAndReturn(
                controller,
                request,
                response,
                DecisionApplyResult.STALE_MATCH,
                false,
                error,
                currentTick,
                List.of(),
                previousPlanId,
                null
            );
        }

        if (match.phase() != MatchPhase.ROUND_ACTIVE
            || match.currentRound() != context.round()) {
            finishDecision(controller, context, currentTick);
            return logAndReturn(
                controller,
                request,
                response,
                DecisionApplyResult.STALE_ROUND,
                false,
                error,
                currentTick,
                List.of(),
                previousPlanId,
                null
            );
        }

        if (!controller.ownerUuid().equals(
            context.ownerUuid()
        )) {
            finishDecision(controller, context, currentTick);
            return logAndReturn(
                controller,
                request,
                response,
                DecisionApplyResult.STALE_ENTITY,
                false,
                error,
                currentTick,
                List.of(),
                previousPlanId,
                null
            );
        }

        if (controller.decisionGeneration()
            != context.generation()) {
            return logAndReturn(
                controller,
                request,
                response,
                DecisionApplyResult.STALE_GENERATION,
                false,
                error,
                currentTick,
                List.of(),
                previousPlanId,
                null
            );
        }

        UUID currentEntityUuid = controller.entityUuid()
            .orElse(null);

        if (!Objects.equals(
            currentEntityUuid,
            context.robotEntityUuid()
        )) {
            finishDecision(controller, context, currentTick);
            controller.requestRedecision(
                DecisionTrigger.STALE_RETRY
            );

            return logAndReturn(
                controller,
                request,
                response,
                DecisionApplyResult.STALE_ENTITY,
                false,
                error,
                currentTick,
                List.of(),
                previousPlanId,
                null
            );
        }

        PlayerSlot slot = match.player(
            controller.ownerUuid()
        ).orElse(null);

        if (slot == null
            || slot.doctrine().isEmpty()
            || slot.doctrine().orElseThrow().version()
                != context.doctrineVersion()) {
            finishDecision(controller, context, currentTick);
            controller.requestRedecision(
                DecisionTrigger.STALE_RETRY
            );

            return logAndReturn(
                controller,
                request,
                response,
                DecisionApplyResult.STALE_DOCTRINE,
                false,
                error,
                currentTick,
                List.of(),
                previousPlanId,
                null
            );
        }

        List<TacticalPlan> currentCandidates =
            validPlanFactory.create(
                match,
                controller,
                currentTick
            );

        Map<String, TacticalPlan> byId =
            indexCandidates(currentCandidates);

        List<String> currentIds =
            List.copyOf(byId.keySet());

        if (error != null || response == null) {
            DecisionApplyResult result = applyFallback(
                controller,
                byId,
                currentTick,
                DecisionApplyResult.API_ERROR_FALLBACK
            );

            finishDecision(controller, context, currentTick);

            return logAndReturn(
                controller,
                request,
                response,
                result,
                true,
                error,
                currentTick,
                currentIds,
                previousPlanId,
                null
            );
        }

        double minimumConfidence =
            config.ai().minimumConfidence();

        DecisionComposition requestComposition =
            composer.compose(
                response,
                request.validPlanIds(),
                previousPlanId,
                minimumConfidence
            );

        DecisionComposition currentComposition =
            composer.compose(
                response,
                currentIds,
                previousPlanId,
                minimumConfidence
            );

        String composedPlanId =
            currentComposition.planId();

        if (composedPlanId == null) {
            if (currentComposition.targetUnavailable()) {
                finishDecision(
                    controller,
                    context,
                    currentTick
                );
                controller.requestRedecision(
                    DecisionTrigger.TARGET_INVALIDATED
                );

                return logAndReturn(
                    controller,
                    request,
                    response,
                    DecisionApplyResult.TARGET_UNAVAILABLE,
                    false,
                    null,
                    currentTick,
                    currentIds,
                    previousPlanId,
                    null
                );
            }

            DecisionApplyResult fallbackResult =
                currentComposition.lowConfidence()
                    ? DecisionApplyResult
                        .LOW_CONFIDENCE_FALLBACK
                    : DecisionApplyResult
                        .UNRESOLVABLE_DECISION;

            DecisionApplyResult result = applyFallback(
                controller,
                byId,
                currentTick,
                fallbackResult
            );

            finishDecision(controller, context, currentTick);

            return logAndReturn(
                controller,
                request,
                response,
                result,
                true,
                null,
                currentTick,
                currentIds,
                previousPlanId,
                null
            );
        }

        TacticalPlan selected = byId.get(composedPlanId);

        if (selected == null) {
            DecisionApplyResult result = applyFallback(
                controller,
                byId,
                currentTick,
                DecisionApplyResult.INVALID_PLAN
            );

            finishDecision(controller, context, currentTick);

            return logAndReturn(
                controller,
                request,
                response,
                result,
                true,
                null,
                currentTick,
                currentIds,
                previousPlanId,
                composedPlanId
            );
        }

        if (composedPlanId.equals(previousPlanId)) {
            finishDecision(controller, context, currentTick);

            return logAndReturn(
                controller,
                request,
                response,
                DecisionApplyResult.KEPT_CURRENT_PLAN,
                currentComposition.lowConfidence(),
                null,
                currentTick,
                currentIds,
                previousPlanId,
                composedPlanId
            );
        }

        boolean applied = planExecutor.assignPlan(
            controller,
            selected,
            currentTick
        );

        finishDecision(controller, context, currentTick);

        if (!applied) {
            controller.requestRedecision(
                DecisionTrigger.STALE_RETRY
            );

            return logAndReturn(
                controller,
                request,
                response,
                DecisionApplyResult.KEPT_CURRENT_PLAN,
                currentComposition.lowConfidence(),
                null,
                currentTick,
                currentIds,
                previousPlanId,
                composedPlanId
            );
        }

        boolean recomposed =
            requestComposition.planId() != null
            && !requestComposition.planId()
                .equals(composedPlanId);

        DecisionApplyResult result = recomposed
            ? DecisionApplyResult.APPLIED_RECOMPOSED
            : DecisionApplyResult.APPLIED;

        return logAndReturn(
            controller,
            request,
            response,
            result,
            currentComposition.lowConfidence(),
            null,
            currentTick,
            currentIds,
            previousPlanId,
            composedPlanId
        );
    }

    private DecisionApplyResult applyFallback(
        RobotController controller,
        Map<String, TacticalPlan> byId,
        long currentTick,
        DecisionApplyResult fallbackResult
    ) {
        TacticalPlan current = controller.currentPlan()
            .orElse(null);

        if (current != null
            && byId.containsKey(current.externalId())) {
            return DecisionApplyResult.KEPT_CURRENT_PLAN;
        }

        TacticalPlan fallback = chooseFallback(
            controller,
            byId
        );

        if (fallback == null) {
            controller.clearPlan();
            controller.requestRedecision(
                DecisionTrigger.STALE_RETRY
            );
            return fallbackResult;
        }

        planExecutor.assignPlan(
            controller,
            fallback,
            currentTick
        );

        return fallbackResult;
    }

    private TacticalPlan chooseFallback(
        RobotController controller,
        Map<String, TacticalPlan> byId
    ) {
        double hpRatio = controller.entity()
            .map(entity ->
                entity.getMaxHealth() <= 0.0F
                    ? 0.0D
                    : entity.getHealth()
                        / entity.getMaxHealth()
            )
            .orElse(0.0D);

        if (hpRatio <= config.ai().fallbackRetreatHpRatio()
            && byId.containsKey("RETREAT")) {
            return byId.get("RETREAT");
        }

        TacticalPlan objective = byId.get(
            "CAPTURE_CORE"
        );

        if (objective == null) {
            objective = byId.get("DEFEND_CORE");
        }

        if (objective != null) {
            return objective;
        }

        return byId.values()
            .stream()
            .findFirst()
            .orElse(null);
    }

    private Map<String, TacticalPlan> indexCandidates(
        List<TacticalPlan> candidates
    ) {
        Map<String, TacticalPlan> byId =
            new LinkedHashMap<>();

        for (TacticalPlan plan : candidates) {
            byId.put(plan.externalId(), plan);
        }

        return byId;
    }

    private DecisionApplyResult logAndReturn(
        RobotController controller,
        DecisionRequest request,
        DecisionResponse response,
        DecisionApplyResult result,
        boolean fallback,
        Throwable error,
        long currentTick,
        List<String> applyValidPlanIds,
        String previousPlanId,
        String composedPlanId
    ) {
        DecisionContext context = request.context();
        RobotSnapshot self = request.snapshot().self();
        CoreSnapshot core = request.snapshot().core();
        TeamContextSnapshot teamContext =
            request.snapshot().teamContext();

        long latencyMs = response != null
            ? response.latencyMs()
            : Math.max(
                0L,
                currentTick - context.requestedTick()
            ) * 50L;

        double hpRatio = self.maxHp() <= 0.0F
            ? 0.0D
            : self.hp() / self.maxHp();

        Throwable rootError = unwrapError(error);
        String errorClass = rootError == null
            ? null
            : rootError.getClass().getName();
        String errorMessage = rootError == null
            ? null
            : truncate(
                rootError.getMessage(),
                1024
            );
        Integer httpStatus =
            rootError instanceof JevRequestException requestError
                ? requestError.httpStatus()
                : null;

        boolean candidateSetChanged =
            !java.util.Set.copyOf(
                applyValidPlanIds
            ).equals(
                java.util.Set.copyOf(
                    request.validPlanIds()
                )
            );

        logs.append(
            new DecisionLog(
                3,
                "TEAM_DECOMPOSED_V3",
                modVersion(),
                context.trigger(),
                context.inFlightAtRequest(),
                context.matchId(),
                context.round(),
                context.requestedTick(),
                currentTick,
                context.ownerUuid(),
                context.robotEntityUuid(),
                controller.color(),
                controller.targetId(),
                controller.team(),
                context.doctrineVersion(),
                request.validPlanIds(),
                applyValidPlanIds,
                candidateSetChanged,
                previousPlanId,
                response == null
                    ? null
                    : response.strategicIntent(),
                response == null
                    ? null
                    : response.combatTarget(),
                response == null
                    ? null
                    : response.pursuitStyle(),
                composedPlanId,
                controller.currentPlan()
                    .map(TacticalPlan::externalId)
                    .orElse(null),
                latencyMs,
                fallback,
                result,
                self.hp(),
                self.maxHp(),
                hpRatio,
                teamContext.teamScore(),
                teamContext.enemyTeamScore(),
                teamContext.aliveAllies(),
                teamContext.aliveEnemies(),
                teamContext.alliesInsideCore(),
                teamContext.enemiesInsideCore(),
                core.ownerTeam(),
                core.contested(),
                errorClass,
                errorMessage,
                httpStatus,
                controller.positionSnapshot()
                    .map(DecisionPosition::from)
                    .orElse(null),
                controller.targetPositionSnapshot()
                    .map(DecisionPosition::from)
                    .orElse(null),
                controller.destinationSnapshot()
                    .map(DecisionPosition::from)
                    .orElse(null),
                finiteOrNull(
                    controller.distanceToCoreSnapshot()
                ),
                finiteOrNull(
                    controller.distanceToTargetSnapshot()
                )
            )
        );

        return result;
    }

    private String modVersion() {
        return net.fabricmc.loader.api.FabricLoader
            .getInstance()
            .getModContainer("autobattle")
            .map(container ->
                container.getMetadata()
                    .getVersion()
                    .getFriendlyString()
            )
            .orElse("unknown");
    }

    private Throwable unwrapError(Throwable error) {
        Throwable current = error;

        while (current != null
            && (current
                instanceof java.util.concurrent.CompletionException
                || current
                instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }

        return current;
    }

    private String truncate(
        String value,
        int maxLength
    ) {
        if (value == null
            || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength)
            + "...";
    }

    private Double finiteOrNull(double value) {
        return Double.isFinite(value)
            ? value
            : null;
    }

    private void finishDecision(
        RobotController controller,
        DecisionContext context,
        long currentTick
    ) {
        controller.markDecisionCompleted(
            context.generation(),
            currentTick
        );
    }
}
