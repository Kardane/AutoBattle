package dev.kardane.autobattle;

import dev.kardane.autobattle.command.AutoBattleCommands;
import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.event.AutoBattleEvents;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.robot.RobotFactory;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoBattleMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "autobattle";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MatchManager matchManager;

    @Override
    public void onInitializeServer() {
        AutoBattleConfig config = AutoBattleConfig.defaults();
        RobotFactory robotFactory = new RobotFactory();

        matchManager = new MatchManager(config);

        AutoBattleCommands.register(matchManager, robotFactory);
        AutoBattleEvents.register(matchManager);

        LOGGER.info(
            "AutoBattle initialized (minimumPlayers={}, rounds={})",
            config.minimumPlayers(),
            config.roundCount()
        );
    }

    public static MatchManager matchManager() {
        if (matchManager == null) {
            throw new IllegalStateException("AutoBattle has not been initialized yet.");
        }
        return matchManager;
    }
}
