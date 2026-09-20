package dev.kardane.autobattle.event;

import dev.kardane.autobattle.match.MatchManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class AutoBattleEvents {
    private AutoBattleEvents() {
    }

    public static void register(MatchManager matchManager) {
        ServerTickEvents.END_SERVER_TICK.register(matchManager::tick);
    }
}
