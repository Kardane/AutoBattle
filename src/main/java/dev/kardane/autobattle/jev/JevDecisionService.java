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
    private static final double MIN_CONFIDENCE = 0.35D;

    private final JevClient client;
    private final RobotStateSerializer serializer;
    private final ValidPlanFactory validPlanFactory;
    private final PlanExecutor planExecutor;
    private final AutoBattleConfig config;

    public JevDecisionService(
        JevClient client,
        RobotStateSerializer serializer,
        ValidPlanFactory validPlanFactory,
        PlanExecutor planExecutor,
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
        this.config = Objects.requireNonNull(config, "config");
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
                        context,
                        response,
                        error
                    )
                )
            );
    }

    private DecisionApplyResult applyResponse(
        MatchSession match,
        RobotController controller,
        DecisionContext context,
        DecisionResponse response,
        Throwable error
    ) {
        long currentTick = context.requestedTick();

        if (!match.matchId().equals(context.matchId())) {
            return DecisionApplyResult.STALE_MATCH;
        }

        if (match.phase() != MatchPhase.ROUND_ACTIVE
            || match.currentRound() != context.round()) {
            finishDecision(controller, context, currentTick);
            return DecisionApplyResult.STALE_ROUND;
        }

        if (!controller.ownerUuid().equals(
            context.ownerUuid()
        )) {
            finishDecision(controller, context, currentTick);
            return DecisionApplyResult.STALE_ENTITY;
        }

        if (controller.decisionGeneration()
            != context.generation()) {
            return DecisionApplyResult.STALE_GENERATION;
        }

        UUID currentEntityUuid = controller.entityUuid()
            .orElse(null);

        if (!Objects.equals(
            currentEntityUuid,
            context.robotEntityUuid()
        )) {
            finishDecision(controller, context, currentTick);
            controller.requestRedecision();
            return DecisionApplyResult.STALE_ENTITY;
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
            return DecisionApplyResult.STALE_DOCTRINE;
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
            return DecisionApplyResult.STALE_CANDIDATES;
        }

        if (error != null || response == null) {
            DecisionApplyResult result = applyFallback(
                controller,
                byId,
                currentTick,
                DecisionApplyResult.API_ERROR_FALLBACK
            );
            finishDecision(controller, context, currentTick);
            return result;
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
            return result;
        }

        if (response.confidence() < MIN_CONFIDENCE) {
            DecisionApplyResult result = applyFallback(
                controller,
                byId,
                currentTick,
                DecisionApplyResult.LOW_CONFIDENCE_FALLBACK
            );
            finishDecision(controller, context, currentTick);
            return result;
        }

        boolean applied = planExecutor.assignPlan(
            controller,
            selected,
            currentTick
        );

        finishDecision(controller, context, currentTick);

        if (!applied) {
            controller.requestRedecision();
            return DecisionApplyResult.KEPT_CURRENT_PLAN;
        }

        return DecisionApplyResult.APPLIED;
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

        if (hpRatio <= 0.25D
            && byId.containsKey("RETREAT")) {
            return byId.get("RETREAT");
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
