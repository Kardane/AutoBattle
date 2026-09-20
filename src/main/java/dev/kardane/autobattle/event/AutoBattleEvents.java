package dev.kardane.autobattle.event;

import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.tactics.PlanExecutor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class AutoBattleEvents {
    private AutoBattleEvents() {
    }

    public static void register(
        MatchManager matchManager,
        PlanExecutor planExecutor
    ) {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            matchManager.tick(server);
            planExecutor.tick(matchManager.serverTick());
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(
            server -> planExecutor.clear()
        );
    }
}
