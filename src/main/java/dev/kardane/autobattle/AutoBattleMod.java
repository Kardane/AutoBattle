package dev.kardane.autobattle;

import dev.kardane.autobattle.command.AutoBattleCommands;
import dev.kardane.autobattle.command.PlayerCommandService;
import dev.kardane.autobattle.config.AutoBattleConfig;
import dev.kardane.autobattle.doctrine.DoctrineService;
import dev.kardane.autobattle.doctrine.DoctrineValidator;
import dev.kardane.autobattle.event.AutoBattleEvents;
import dev.kardane.autobattle.jev.JevDecisionService;
import dev.kardane.autobattle.jev.RobotStateSerializer;
import dev.kardane.autobattle.jev.ScriptedJevClient;
import dev.kardane.autobattle.match.MatchManager;
import dev.kardane.autobattle.robot.RobotFactory;
import dev.kardane.autobattle.robot.RobotRegistry;
import dev.kardane.autobattle.tactics.PlanExecutor;
import dev.kardane.autobattle.tactics.ValidPlanFactory;
import dev.kardane.autobattle.ui.UiCoordinator;
import net.fabricmc.api.DedicatedServerModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoBattleMod implements DedicatedServerModInitializer {
    public static final String MOD_ID = "autobattle";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MatchManager matchManager;
    private static PlanExecutor planExecutor;

    @Override
    public void onInitializeServer() {
        AutoBattleConfig config = AutoBattleConfig.defaults();
        RobotFactory robotFactory = new RobotFactory();
        RobotRegistry robotRegistry = new RobotRegistry();
        planExecutor = new PlanExecutor(robotRegistry);
        UiCoordinator ui = new UiCoordinator(
            config,
            planExecutor
        );
        PlayerCommandService commandService =
            new PlayerCommandService(
                config,
                planExecutor
            );
        JevDecisionService decisionService =
            new JevDecisionService(
                new ScriptedJevClient(),
                new RobotStateSerializer(),
                new ValidPlanFactory(),
                planExecutor,
                config
            );
        DoctrineService doctrineService = new DoctrineService(
            new DoctrineValidator()
        );

        matchManager = new MatchManager(
            config,
            robotRegistry,
            robotFactory,
            planExecutor,
            commandService,
            decisionService,
            ui
        );

        AutoBattleCommands.register(
            matchManager,
            robotFactory,
            planExecutor,
            doctrineService,
            commandService
        );

        AutoBattleEvents.register(
            matchManager,
            planExecutor
        );

        LOGGER.info(
            "AutoBattle initialized (minimumPlayers={}, rounds={})",
            config.minimumPlayers(),
            config.roundCount()
        );
    }

    public static MatchManager matchManager() {
        if (matchManager == null) {
            throw new IllegalStateException(
                "AutoBattle has not been initialized yet."
            );
        }

        return matchManager;
    }

    public static PlanExecutor planExecutor() {
        if (planExecutor == null) {
            throw new IllegalStateException(
                "AutoBattle has not been initialized yet."
            );
        }

        return planExecutor;
    }
}
