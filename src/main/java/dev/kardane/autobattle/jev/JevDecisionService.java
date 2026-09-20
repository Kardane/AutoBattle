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
                config.decisionLockTicks()
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

        long generation = controller.nextDecisionGeneration();

        DecisionContext context = new DecisionContext(
            match.matchId(),
            match.currentRound(),
            controller.ownerUuid(),
            entityUuid,
            slot.doctrine().orElseThrow().version(),
            generation,
            currentTick,
            validPlanIds.hashCode()
        );

        RobotDecisionSnapshot snapshot =
            serializer.snapshot(
                match,
                controller,
                currentTick
            );

        DecisionRequest request = new DecisionRequest(
            context,
            snapshot,
            validPlanIds
        );

        controller.markDecisionRequested(generation);

        client.decide(request)
            .orTimeout(
                config.jevTimeoutMs(),
                TimeUnit.MILLISECONDS
            )
            .whenComplete((response, error) ->
                server.execute(() ->
                    applyResponse(
                        match,
                        controller,
                        request,
                        response,
                        error,
                        server.getTickCount()
                    )
                )
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

        if (!match.matchId().equals(context.matchId())) {
            return logAndReturn(
                controller,
                request,
                response,
                DecisionApplyResult.STALE_MATCH,
                false,
                currentTick
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
                currentTick
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
                currentTick
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
                currentTick
            );
        }

        UUID currentEntityUuid = controller.entityUuid()
            .orElse(null);

        if (!Objects.equals(
            currentEntityUuid,
            context.robotEntityUuid()
        )) {
            finishDecision(controller, context, currentTick);
            controller.requestRedecision();

            return logAndReturn(
                controller,
                request,
                response,
                DecisionApplyResult.STALE_ENTITY,
                false,
                currentTick
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
            controller.requestRedecision();

            return logAndReturn(
                controller,
                request,
                response,
                DecisionApplyResult.STALE_DOCTRINE,
                false,
                currentTick
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

        if (currentIds.hashCode()
            != context.candidatesHash()) {
            finishDecision(controller, context, currentTick);
            controller.requestRedecision();

            return logAndReturn(
                controller,
                request,
                response,
                DecisionApplyResult.STALE_CANDIDATES,
                false,
                currentTick
            );
        }

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
                currentTick
            );
        }

        TacticalPlan selected = byId.get(
            response.selectedPlanId()
        );

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
                currentTick
            );
        }

        if (response.confidence() < config.ai().minimumConfidence()) {
            DecisionApplyResult result = applyFallback(
                controller,
                byId,
                currentTick,
                DecisionApplyResult.LOW_CONFIDENCE_FALLBACK
            );

            finishDecision(controller, context, currentTick);

            return logAndReturn(
                controller,
                request,
                response,
                result,
                true,
                currentTick
            );
        }

        boolean applied = planExecutor.assignPlan(
            controller,
            selected,
            currentTick
        );

        finishDecision(controller, context, currentTick);

        DecisionApplyResult result;

        if (!applied) {
            controller.requestRedecision();
            result = DecisionApplyResult.KEPT_CURRENT_PLAN;
        } else {
            result = DecisionApplyResult.APPLIED;
        }

        return logAndReturn(
            controller,
            request,
            response,
            result,
            false,
            currentTick
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
            controller.requestRedecision();
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

        TacticalPlan reposition = byId.get("REPOSITION");

        if (reposition != null) {
            return reposition;
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
        long currentTick
    ) {
        DecisionContext context = request.context();

        long latencyMs = response != null
            ? response.latencyMs()
            : Math.max(
                0L,
                currentTick - context.requestedTick()
            ) * 50L;

        logs.append(
            new DecisionLog(
                context.matchId(),
                context.round(),
                context.requestedTick(),
                currentTick,
                context.ownerUuid(),
                context.robotEntityUuid(),
                controller.color(),
                context.doctrineVersion(),
                request.validPlanIds(),
                response == null
                    ? null
                    : response.selectedPlanId(),
                controller.currentPlan()
                    .map(TacticalPlan::externalId)
                    .orElse(null),
                response == null
                    ? 0.0D
                    : response.confidence(),
                response == null
                    ? Map.of()
                    : response.probabilities(),
                latencyMs,
                fallback,
                result,
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
