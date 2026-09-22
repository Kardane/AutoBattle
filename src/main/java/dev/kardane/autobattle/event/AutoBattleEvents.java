package dev.kardane.autobattle.event;

import dev.kardane.autobattle.jev.JevDecisionService;
import dev.kardane.autobattle.log.MatchLogService;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.tactics.PlanExecutor;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public final class AutoBattleEvents {
    private AutoBattleEvents() {
    }

    public static void register(
        MatchManager matchManager,
        PlanExecutor planExecutor,
        JevDecisionService decisionService,
        MatchLogService matchLogs
    ) {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            matchManager.tick(server);
            planExecutor.tick(
                matchManager.session(),
                matchManager.serverTick()
            );
            matchManager.resolveRobotCombat();
        });

        ServerPlayConnectionEvents.DISCONNECT.register(
            (handler, server) ->
                matchManager.handleDisconnect(
                    handler.player,
                    server
                )
        );

        ServerLivingEntityEvents.ALLOW_DAMAGE.register(
            matchManager::allowDamage
        );

        ServerLivingEntityEvents.AFTER_DAMAGE.register(
            matchManager::handleAfterDamage
        );

        ServerLivingEntityEvents.AFTER_DEATH.register(
            matchManager::handleAfterDeath
        );

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            matchManager.handleServerStopped(server);
            planExecutor.clear();
            decisionService.logs().close();
            matchLogs.close();
        });
    }
}
